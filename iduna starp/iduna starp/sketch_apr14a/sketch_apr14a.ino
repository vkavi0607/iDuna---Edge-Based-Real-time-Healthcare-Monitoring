#include <Wire.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "MAX30105.h"
#include <TFT_eSPI.h>

TFT_eSPI tft = TFT_eSPI();

// ---- Display colour palette (RGB565) -------------------------
#define CLR_BG         0x0000
#define CLR_ACCENT     0xF800
#define CLR_LABEL      0x7BEF
#define CLR_VALUE      0xFFFF
#define CLR_BLE_ON     0x001F
#define CLR_BLE_OFF    0x39E7
#define CLR_WARN       0xFD20
#define CLR_ANOM_BG    0x2800
#define CLR_NORMAL_BG  0x0180
#define CLR_BRADY_FG   0x351F
#define CLR_IREG_FG    0xF81F
#define CLR_CRIT_BG    0x4000

// ---- BLE UUIDs -----------------------------------------------
#define HR_SERVICE_UUID   "0000180D-0000-1000-8000-00805f9b34fb"
#define HR_CHAR_UUID      "00002A37-0000-1000-8000-00805f9b34fb"
#define AVG_HR_CHAR_UUID  "00002A39-0000-1000-8000-00805f9b34fb"

// ---- Timing --------------------------------------------------
#define SAMPLE_RATE_HZ     100
#define SAMPLE_INTERVAL_MS (1000 / SAMPLE_RATE_HZ)

// ---- Finger Detection ----------------------------------------
#define FINGER_ON_THRESHOLD   50000UL
#define FINGER_OFF_THRESHOLD  30000UL
#define FINGER_DEBOUNCE_MS    500

// ---- DSP: DC Removal -----------------------------------------
#define DC_WINDOW_SIZE  200

// ---- DSP: Bandpass (0.5-4 Hz @ 100 Hz) ----------------------
#define BP_STAGES 2
static const float bpCoeffs[BP_STAGES][5] = {
  { 0.00289819f,  0.00579639f,  0.00289819f, -1.82269493f,  0.83428770f },
  { 0.97077640f, -1.94155280f,  0.97077640f, -1.94111477f,  0.94199084f }
};

// ---- DSP: Peak Detection -------------------------------------
#define PEAK_BUF_SIZE       60
#define PEAK_MIN_IBI_MS     250
#define PEAK_MAX_IBI_MS     1500
#define PEAK_THRESHOLD_INIT 0.5f
#define PEAK_ADAPT_ALPHA    0.1f
#define PEAK_REFRACTORY_MS  300

// ---- Beat / Avg buffers --------------------------------------
#define BEAT_BUF_SIZE      12
#define OUTLIER_THRESH_BPM 20
#define AVG_WINDOW_SIZE    10

// ---- Serial intervals ----------------------------------------
#define STATUS_PRINT_MS      1000
#define PLACE_FINGER_MSG_MS  3000

// ---- Anomaly -------------------------------------------------
#define ANOM_NONE      0x00
#define ANOM_TACHY     0x01
#define ANOM_BRADY     0x02
#define ANOM_IRREGULAR 0x04
#define ANOM_PAUSE     0x08
#define ANOM_VARIANCE  0x10

// ==============================================================
//  Globals
// ==============================================================
MAX30105           particleSensor;
BLEServer*         pServer    = nullptr;
BLECharacteristic* pHrChar    = nullptr;
BLECharacteristic* pAvgHrChar = nullptr;

bool     deviceConnected = false;
bool     prevConnected   = false;
uint32_t lastSendTs      = 0;

bool     fingerOn         = false;
uint32_t fingerDebounceTs = 0;

uint32_t lastSampleTs         = 0;
uint32_t lastStatusPrintTs    = 0;
uint32_t lastPlaceFingerMsgTs = 0;

float    dcBuffer[DC_WINDOW_SIZE] = {0};
uint16_t dcIdx    = 0;
float    dcSum    = 0.0f;
bool     dcFilled = false;

float bpZ[BP_STAGES][2] = {{0}};

float    peakSearchBuf[PEAK_BUF_SIZE] = {0};
uint8_t  peakBufIdx    = 0;
float    peakThreshold = PEAK_THRESHOLD_INIT;
uint32_t lastPeakTs    = 0;
uint8_t  peakBufFillCount = 0;

uint32_t ibiBuf[BEAT_BUF_SIZE] = {0};
uint8_t  ibiBufIdx   = 0;
uint8_t  ibiBufCount = 0;

float   smoothedBPM = 0.0f;
uint8_t currentBPM  = 0;
uint32_t prevPeakTs = 0;

uint8_t hrHistory[AVG_WINDOW_SIZE] = {0};
uint8_t hrHistIdx   = 0;
uint8_t hrHistCount = 0;

bool dspResetPending = false;

uint8_t  currentAnomaly     = ANOM_NONE;
uint8_t  prevBpmForVariance = 0;
uint32_t tachyStartTs       = 0;
uint32_t bradyStartTs       = 0;

static uint8_t disp_lastBPM     = 255;
static uint8_t disp_lastAvg     = 255;
static bool    disp_lastFinger  = false;
static bool    disp_lastBLE     = false;
static uint8_t disp_lastAnomaly = 0xFF;

// ==============================================================
//  DSP FUNCTIONS
// ==============================================================
float removeDC(float sample) {
  dcSum -= dcBuffer[dcIdx];
  dcBuffer[dcIdx] = sample;
  dcSum += sample;
  dcIdx = (dcIdx + 1) % DC_WINDOW_SIZE;
  if (!dcFilled && dcIdx == 0) dcFilled = true;
  uint16_t n = dcFilled ? DC_WINDOW_SIZE : (dcIdx == 0 ? DC_WINDOW_SIZE : dcIdx);
  return sample - (dcSum / n);
}

float biquadFilter(float x) {
  float y = x;
  for (uint8_t s = 0; s < BP_STAGES; s++) {
    const float* c = bpCoeffs[s];
    float out  = c[0] * y + bpZ[s][0];
    bpZ[s][0]  = c[1] * y - c[3] * out + bpZ[s][1];
    bpZ[s][1]  = c[2] * y - c[4] * out;
    y = out;
  }
  return y;
}

float normalise(float sample) {
  float mean = 0.0f;
  for (uint8_t i = 0; i < PEAK_BUF_SIZE; i++) mean += peakSearchBuf[i];
  mean /= (float)PEAK_BUF_SIZE;
  float var = 0.0f;
  for (uint8_t i = 0; i < PEAK_BUF_SIZE; i++) {
    float d = peakSearchBuf[i] - mean;
    var += d * d;
  }
  float stddev = sqrtf(var / (float)PEAK_BUF_SIZE);
  if (stddev < 1e-4f) return 0.0f;
  return (sample - mean) / stddev;
}

bool detectPeak(uint32_t nowMs) {
  uint8_t currIdx = (peakBufIdx + PEAK_BUF_SIZE - 1) % PEAK_BUF_SIZE;
  uint8_t candIdx = (peakBufIdx + PEAK_BUF_SIZE - 2) % PEAK_BUF_SIZE;
  uint8_t prevIdx = (peakBufIdx + PEAK_BUF_SIZE - 3) % PEAK_BUF_SIZE;

  float s_curr = peakSearchBuf[currIdx];
  float s_cand = peakSearchBuf[candIdx];
  float s_prev = peakSearchBuf[prevIdx];

  if (lastPeakTs > 0 && (nowMs - lastPeakTs) < PEAK_REFRACTORY_MS) {
    if (s_cand > peakThreshold)
      peakThreshold = peakThreshold * (1.0f - PEAK_ADAPT_ALPHA)
                    + s_cand * 0.5f * PEAK_ADAPT_ALPHA;
    return false;
  }

  if (s_cand > peakThreshold) {
    peakThreshold = peakThreshold * (1.0f - PEAK_ADAPT_ALPHA)
                  + s_cand * 0.5f * PEAK_ADAPT_ALPHA;
  } else {
    peakThreshold *= 0.9998f;
    if (peakThreshold < PEAK_THRESHOLD_INIT) peakThreshold = PEAK_THRESHOLD_INIT;
  }

  if ((s_cand > s_prev) && (s_cand > s_curr) && (s_cand > peakThreshold)) {
    lastPeakTs = nowMs;
    return true;
  }
  return false;
}

bool validateIBI(uint32_t ibi) {
  return (ibi >= PEAK_MIN_IBI_MS && ibi <= PEAK_MAX_IBI_MS);
}

bool isOutlier(float newBPM) {
  if (ibiBufCount < 4) return false;
  uint8_t n = (ibiBufCount < BEAT_BUF_SIZE) ? ibiBufCount : BEAT_BUF_SIZE;
  float arr[BEAT_BUF_SIZE];
  for (uint8_t i = 0; i < n; i++) arr[i] = 60000.0f / (float)ibiBuf[i];
  for (uint8_t i = 1; i < n; i++) {
    float key = arr[i]; int8_t j = i - 1;
    while (j >= 0 && arr[j] > key) { arr[j+1] = arr[j]; j--; }
    arr[j+1] = key;
  }
  return (fabsf(newBPM - arr[n / 2]) > OUTLIER_THRESH_BPM);
}

float weightedAvgBPM() {
  uint8_t n = (ibiBufCount < BEAT_BUF_SIZE) ? ibiBufCount : BEAT_BUF_SIZE;
  if (n == 0) return 0.0f;
  float ws = 0.0f, tw = 0.0f;
  for (uint8_t i = 0; i < n; i++) {
    uint8_t  idx = (ibiBufIdx + BEAT_BUF_SIZE - 1 - i) % BEAT_BUF_SIZE;
    uint32_t ibi = ibiBuf[idx];
    if (ibi == 0) continue;
    float w = (float)(n - i);
    ws += (60000.0f / (float)ibi) * w;
    tw += w;
  }
  return (tw < 1.0f) ? 0.0f : ws / tw;
}

void resetDSP() {
  memset(dcBuffer,      0, sizeof(dcBuffer));
  dcIdx = 0; dcSum = 0.0f; dcFilled = false;
  memset(bpZ,           0, sizeof(bpZ));
  memset(peakSearchBuf, 0, sizeof(peakSearchBuf));
  peakBufIdx       = 0;
  peakBufFillCount = 0;
  peakThreshold    = PEAK_THRESHOLD_INIT;
  lastPeakTs       = 0;
  memset(ibiBuf,        0, sizeof(ibiBuf));
  ibiBufIdx   = 0;
  ibiBufCount = 0;
  smoothedBPM = 0.0f;
  currentBPM  = 0;
  prevPeakTs  = 0;
  dspResetPending = false;
  disp_lastBPM    = 255;
  disp_lastAvg    = 255;
  disp_lastAnomaly = 0xFF;
}

// ==============================================================
//  AVG HR
// ==============================================================
void addToAvgHistory(uint8_t bpm) {
  hrHistory[hrHistIdx] = bpm;
  hrHistIdx = (hrHistIdx + 1) % AVG_WINDOW_SIZE;
  if (hrHistCount < AVG_WINDOW_SIZE) hrHistCount++;
}

uint8_t getAvgHR() {
  if (hrHistCount == 0) return 0;
  uint32_t sum = 0;
  for (uint8_t i = 0; i < hrHistCount; i++) sum += hrHistory[i];
  return (uint8_t)(sum / hrHistCount);
}

// ==============================================================
//  ANOMALY DETECTION
// ==============================================================
float ibiStdDev() {
  uint8_t n = (ibiBufCount < BEAT_BUF_SIZE) ? ibiBufCount : BEAT_BUF_SIZE;
  if (n < 3) return 0.0f;
  float sum = 0.0f;
  for (uint8_t i = 0; i < n; i++) sum += (float)ibiBuf[i];
  float mean = sum / n;
  float var  = 0.0f;
  for (uint8_t i = 0; i < n; i++) {
    float d = (float)ibiBuf[i] - mean;
    var += d * d;
  }
  return sqrtf(var / n);
}

void evaluateAnomalies(uint32_t nowMs) {
  if (!fingerOn || ibiBufCount < 4) {
    currentAnomaly = ANOM_NONE;
    tachyStartTs   = 0;
    bradyStartTs   = 0;
    return;
  }
  uint8_t anom = ANOM_NONE;

  if (currentBPM > 100) {
    if (tachyStartTs == 0) tachyStartTs = nowMs;
    else if (nowMs - tachyStartTs >= 10000UL) anom |= ANOM_TACHY;
  } else tachyStartTs = 0;

  if (currentBPM > 0 && currentBPM < 50) {
    if (bradyStartTs == 0) bradyStartTs = nowMs;
    else if (nowMs - bradyStartTs >= 10000UL) anom |= ANOM_BRADY;
  } else bradyStartTs = 0;

  if (ibiStdDev() > 150.0f) anom |= ANOM_IRREGULAR;

  if (ibiBufCount > 0) {
    uint8_t last = (ibiBufIdx + BEAT_BUF_SIZE - 1) % BEAT_BUF_SIZE;
    if (ibiBuf[last] > PEAK_MAX_IBI_MS) anom |= ANOM_PAUSE;
  }

  if (prevBpmForVariance > 0 && currentBPM > 0)
    if (abs((int)currentBPM - (int)prevBpmForVariance) > 20) anom |= ANOM_VARIANCE;
  prevBpmForVariance = currentBPM;

  currentAnomaly = anom;
}

// ==============================================================
//  DISPLAY
// ==============================================================
void drawAnomalyZone() {
  if (currentAnomaly == disp_lastAnomaly) return;
  disp_lastAnomaly = currentAnomaly;

  tft.fillRect(0, 186, 240, 36, CLR_BG);
  if (!fingerOn) return;

  if (currentAnomaly == ANOM_NONE) {
    tft.fillRect(0, 186, 240, 36, CLR_NORMAL_BG);
    tft.setTextColor(0x07E0, CLR_NORMAL_BG);
    tft.setTextSize(1);
    tft.setCursor(42, 196);
    tft.print("STATUS: NORMAL");
    tft.setTextColor(0x0380, CLR_NORMAL_BG);
    tft.setCursor(30, 208);
    tft.print("HR in range, no alerts");
    for (uint8_t i = 0; i < 3; i++)
      tft.fillRect(96 + i * 16, 217, 12, 4, 0x1082);
    return;
  }

  const char* label;
  const char* detail;
  uint16_t fg, bg;
  uint8_t severity;

  if (currentAnomaly & ANOM_TACHY) {
    label="TACHYCARDIA"; detail="HR > 100 BPM  >10s";
    fg=CLR_WARN; bg=CLR_ANOM_BG; severity=2;
  } else if (currentAnomaly & ANOM_BRADY) {
    label="BRADYCARDIA"; detail="HR < 50 BPM  >10s";
    fg=CLR_BRADY_FG; bg=0x0008; severity=2;
  } else if (currentAnomaly & ANOM_IRREGULAR) {
    label="IRREGULAR"; detail="High IBI variance";
    fg=CLR_IREG_FG; bg=0x1001; severity=3;
  } else if (currentAnomaly & ANOM_PAUSE) {
    label="MISSED BEAT"; detail="Long pause detected";
    fg=CLR_ACCENT; bg=CLR_CRIT_BG; severity=3;
  } else {
    label="HIGH VARIANCE"; detail="BPM swings >20";
    fg=CLR_WARN; bg=CLR_ANOM_BG; severity=1;
  }

  tft.fillRect(0, 186, 240, 36, bg);
  tft.fillTriangle(8, 220, 16, 190, 24, 220, fg);
  tft.setTextColor(CLR_BG, fg);
  tft.setTextSize(1);
  tft.setCursor(13, 209);
  tft.print("!");
  tft.setTextColor(fg, bg);
  tft.setTextSize(1);
  tft.setCursor(28, 195);
  tft.print(label);
  tft.setTextColor(0xFB80, bg);
  tft.setCursor(28, 207);
  tft.print(detail);
  for (uint8_t i = 0; i < 3; i++) {
    uint16_t col = (i < severity) ? fg : 0x2104;
    tft.fillRect(192 + i * 14, 215, 10, 4, col);
  }
}

void drawDisplayChrome() {
  tft.fillScreen(CLR_BG);

  tft.fillRect(0, 0, 240, 28, 0x000F);

  tft.fillRect(6,  6,  4, 4, CLR_ACCENT);
  tft.fillRect(11, 6,  4, 4, CLR_ACCENT);
  tft.fillRect(5,  10, 12, 5, CLR_ACCENT);
  tft.fillRect(6,  15, 10, 3, CLR_ACCENT);
  tft.fillRect(8,  18, 6,  3, CLR_ACCENT);

  tft.setTextColor(CLR_VALUE, 0x000F);
  tft.setTextSize(1);
  tft.setCursor(22, 10);
  tft.print("iDuna");

  tft.drawFastHLine(8, 30, 224, CLR_LABEL);
  tft.drawFastHLine(8, 152, 224, CLR_LABEL);
  tft.drawFastHLine(8, 185, 224, CLR_LABEL);
  tft.drawFastHLine(8, 222, 224, CLR_LABEL);

  tft.setTextColor(CLR_LABEL, CLR_BG);
  tft.setTextSize(1);
  tft.setCursor(82, 36);
  tft.print("HEART RATE");

  tft.setTextColor(CLR_LABEL, CLR_BG);
  tft.setTextSize(1);
  tft.setCursor(108, 148);
  tft.print("BPM");

  tft.setTextColor(CLR_LABEL, CLR_BG);
  tft.setTextSize(1);
  tft.setCursor(10, 158);
  tft.print("AVG");
  tft.setCursor(180, 158);
  tft.print("10-beat");

  disp_lastBPM     = 255;
  disp_lastAvg     = 255;
  disp_lastFinger  = !fingerOn;
  disp_lastBLE     = !deviceConnected;
  disp_lastAnomaly = 0xFF;
}

void updateDisplay() {
  if (currentBPM != disp_lastBPM) {
    tft.fillRect(10, 46, 220, 100, CLR_BG);
    if (!fingerOn) {
      tft.setTextColor(CLR_WARN, CLR_BG);
      tft.setTextSize(2);
      tft.setCursor(44, 90);
      tft.print("Place Finger");
    } else if (currentBPM == 0) {
      tft.setTextColor(CLR_LABEL, CLR_BG);
      tft.setTextSize(2);
      tft.setCursor(68, 90);
      tft.print("Warmup...");
    } else {
      uint16_t bpmCol = (currentAnomaly & (ANOM_TACHY | ANOM_BRADY)) ? CLR_WARN : CLR_ACCENT;
      tft.setTextColor(bpmCol, CLR_BG);
      tft.setTextSize(6);
      uint8_t digits = (currentBPM >= 100) ? 3 : 2;
      int16_t x = (240 - digits * 36) / 2;
      tft.setCursor(x, 50);
      tft.print(currentBPM);
    }
    disp_lastBPM = currentBPM;
  }

  uint8_t avg = getAvgHR();
  if (avg != disp_lastAvg) {
    tft.fillRect(50, 158, 140, 26, CLR_BG);
    if (avg == 0) {
      tft.setTextColor(CLR_LABEL, CLR_BG);
      tft.setTextSize(1);
      tft.setCursor(110, 168);
      tft.print("--");
    } else {
      tft.setTextColor(CLR_VALUE, CLR_BG);
      tft.setTextSize(2);
      char buf[10];
      sprintf(buf, "%d BPM", avg);
      uint8_t len = strlen(buf);
      tft.setCursor((240 - len * 12) / 2, 162);
      tft.print(buf);
    }
    disp_lastAvg = avg;
  }

  drawAnomalyZone();

  if (fingerOn != disp_lastFinger || deviceConnected != disp_lastBLE) {
    tft.fillRect(0, 223, 240, 17, CLR_BG);
    tft.fillCircle(10, 231, 5, fingerOn ? CLR_ACCENT : CLR_WARN);
    if (fingerOn) {
      tft.setTextColor(CLR_VALUE, CLR_BG);
      tft.setCursor(20, 227);
      tft.print("Finger on");
    } else {
      tft.setTextColor(CLR_WARN, CLR_BG);
      tft.setCursor(20, 227);
      tft.print("Place finger");
    }
    tft.setTextSize(1);
    if (deviceConnected) {
      tft.setTextColor(CLR_BLE_ON, CLR_BG);
      tft.setCursor(170, 227);
      tft.print("BLE:ON");
    } else {
      tft.setTextColor(CLR_BLE_OFF, CLR_BG);
      tft.setCursor(164, 227);
      tft.print("BLE:ADV");
    }
    disp_lastFinger = fingerOn;
    disp_lastBLE    = deviceConnected;
  }
}

void initDisplay() {
  tft.init();
  tft.setRotation(0);
  tft.fillScreen(CLR_BG);

  tft.setTextColor(CLR_ACCENT);
  tft.setTextSize(3);
  tft.setCursor(75, 80);
  tft.print("iDuna");

  tft.setTextColor(CLR_VALUE);
  tft.setTextSize(2);
  tft.setCursor(72, 115);
  tft.print("Strap v1.0");

  tft.setTextColor(CLR_LABEL);
  tft.setTextSize(1);
  tft.setCursor(30, 150);
  tft.print("Initialising sensors...");

  delay(1500);
  drawDisplayChrome();
}

// ==============================================================
//  BLE CALLBACKS
// ==============================================================
class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer*) override {
    deviceConnected = true;
    BLEDevice::stopAdvertising();
    Serial.print("[BLE] Connected");
    if (!fingerOn) Serial.print(" — place finger on sensor");
    Serial.println();
  }
  void onDisconnect(BLEServer*) override {
    deviceConnected = false;
    prevConnected   = false;
    BLEDevice::startAdvertising();
    Serial.println("[BLE] Disconnected — advertising restarted");
  }
};

// ==============================================================
//  BLE SEND
// ==============================================================
void sendHRData(uint8_t bpm, uint8_t avgBpm) {
  uint8_t hrPkt[3]  = { 0x00, bpm, currentAnomaly };
  uint8_t avgPkt[2] = { 0x00, avgBpm };
  pHrChar->setValue(hrPkt, 3);
  pHrChar->notify();
  pAvgHrChar->setValue(avgPkt, 2);
  pAvgHrChar->notify();
  Serial.print("[TX] HR="); Serial.print(bpm);
  Serial.print(" Avg=");    Serial.print(avgBpm);
  Serial.print(" Anom=0x"); Serial.println(currentAnomaly, HEX);
}

// ==============================================================
//  SETUP
// ==============================================================
void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println("\n=== iDuna ===");

  initDisplay();

  if (!particleSensor.begin(Wire, I2C_SPEED_FAST)) {
    Serial.println("[ERROR] MAX30102 not found");
    tft.fillScreen(CLR_BG);
    tft.setTextColor(CLR_ACCENT);
    tft.setTextSize(2);
    tft.setCursor(20, 100);
    tft.print("MAX30102 ERROR");
    tft.setTextColor(CLR_LABEL);
    tft.setTextSize(1);
    tft.setCursor(20, 130);
    tft.print("Check wiring & restart");
    while (true) delay(1000);
  }
  particleSensor.setup(60, 1, 2, 100, 411, 4096);
  particleSensor.setPulseAmplitudeRed(0x0A);
  particleSensor.setPulseAmplitudeGreen(0);
  Serial.println("[INIT] MAX30102 OK");

  BLEDevice::init("iDuna");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService* pSvc = pServer->createService(HR_SERVICE_UUID);

  pHrChar = pSvc->createCharacteristic(
    HR_CHAR_UUID,
    BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
  );
  pHrChar->addDescriptor(new BLE2902());

  pAvgHrChar = pSvc->createCharacteristic(
    AVG_HR_CHAR_UUID,
    BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
  );
  pAvgHrChar->addDescriptor(new BLE2902());

  uint8_t z[2] = { 0x00, 0x00 };
  pHrChar->setValue(z, 2);
  pAvgHrChar->setValue(z, 2);
  pSvc->start();

  BLEAdvertising* pAdv = BLEDevice::getAdvertising();
  pAdv->addServiceUUID(HR_SERVICE_UUID);
  pAdv->setScanResponse(true);
  pAdv->setMinPreferred(0x06);
  pAdv->setMaxPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("[BLE] Advertising as 'iDuna'");
  Serial.println("[WAIT] Place finger on sensor\n");
  Serial.println("STATUS        | HR  | AVG | ANOM");
  Serial.println("--------------|-----|-----|-----");

  updateDisplay();
}

// ==============================================================
//  MAIN LOOP
// ==============================================================
void loop() {
  uint32_t now = millis();

  if (now - lastSampleTs < SAMPLE_INTERVAL_MS) return;
  lastSampleTs = now;

  particleSensor.check();
  if (!particleSensor.available()) return;
  uint32_t irRaw = particleSensor.getIR();
  particleSensor.nextSample();

  if (deviceConnected && !prevConnected) prevConnected = true;

  // ---- Finger detection --------------------------------------
  if (!fingerOn) {
    if (irRaw > FINGER_ON_THRESHOLD) {
      if (fingerDebounceTs == 0) fingerDebounceTs = now;
      else if (now - fingerDebounceTs >= FINGER_DEBOUNCE_MS) {
        fingerOn = true;
        fingerDebounceTs = 0;
        resetDSP();
        lastPlaceFingerMsgTs = now;
        Serial.println("[FINGER] Detected — measuring...");
        updateDisplay();
      }
    } else {
      fingerDebounceTs = 0;
      if (now - lastPlaceFingerMsgTs >= PLACE_FINGER_MSG_MS) {
        lastPlaceFingerMsgTs = now;
        Serial.println("[WAIT] Place finger on sensor");
      }
    }
    return;
  }

  if (irRaw < FINGER_OFF_THRESHOLD) {
    fingerOn = false;
    fingerDebounceTs = 0;
    currentAnomaly = ANOM_NONE;
    tachyStartTs   = 0;
    bradyStartTs   = 0;
    resetDSP();
    lastPlaceFingerMsgTs = now;
    Serial.println("[FINGER] Removed — place finger again");
    updateDisplay();
    return;
  }

  // ---- DSP Pipeline ------------------------------------------
  float ac       = removeDC((float)irRaw);
  float filtered = biquadFilter(ac);

  peakSearchBuf[peakBufIdx] = filtered;
  float normSample = normalise(filtered);
  peakSearchBuf[peakBufIdx] = normSample;
  peakBufIdx = (peakBufIdx + 1) % PEAK_BUF_SIZE;

  if (peakBufFillCount < PEAK_BUF_SIZE) {
    peakBufFillCount++;
    return;
  }

  bool beat = detectPeak(now);

  if (beat) {
    if (prevPeakTs == 0) {
      prevPeakTs = now;
    } else {
      uint32_t ibi = now - prevPeakTs;
      prevPeakTs = now;
      float bpm = 60000.0f / (float)ibi;

      if (validateIBI(ibi) && !isOutlier(bpm)) {
        ibiBuf[ibiBufIdx] = ibi;
        ibiBufIdx = (ibiBufIdx + 1) % BEAT_BUF_SIZE;
        if (ibiBufCount < BEAT_BUF_SIZE) ibiBufCount++;

        smoothedBPM = weightedAvgBPM();
        currentBPM  = (uint8_t)roundf(smoothedBPM);
        addToAvgHistory(currentBPM);
      }
    }
  }

  // ---- 1 Hz update -------------------------------------------
  if (now - lastStatusPrintTs >= STATUS_PRINT_MS) {
    lastStatusPrintTs = now;

    if (ibiBufCount % 5 == 0 && ibiBufCount > 0) {  // <-- இந்த ஒரு line மாத்து
      evaluateAnomalies(now);
    }
    if (currentBPM > 0) {
      uint8_t avg = getAvgHR();
      Serial.print(deviceConnected ? "[CONNECTED]   | " : "[ADVERTISING] | ");
      Serial.print(currentBPM); Serial.print("  | "); Serial.print(avg);
      if (currentAnomaly) { Serial.print(" | 0x"); Serial.print(currentAnomaly, HEX); }
      Serial.println();
    }

    updateDisplay();
  }

  // ---- BLE notify --------------------------------------------
  if (deviceConnected && (now - lastSendTs >= 1000)) {
    lastSendTs = now;
    if (currentBPM > 0 && ibiBufCount >= 3) {
      sendHRData(currentBPM, getAvgHR());
    }
  }
}
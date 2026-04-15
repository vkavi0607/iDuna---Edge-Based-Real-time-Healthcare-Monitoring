# iDuna — Real-Time Wearable Heart Rate Monitoring Platform
> **Final Year Project** | ESP32 Firmware + Android App (tlin / Jetpack Compose)

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [System Architecture](#2-system-architecture)
3. [Hardware Requirements](#3-hardware-requirements)
4. [Firmware — ESP32 Strap](#4-firmware--esp32-strap)
5. [Android App — iDuna Mobile](#5-android-app--iduna-mobile)
6. [BLE Communication Protocol](#6-ble-communication-protocol)
7. [Setup & Installation](#7-setup--installation)
8. [How It Works — End-to-End Flow](#8-how-it-works--end-to-end-flow)
9. [Project Structure](#9-project-structure)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Project Overview

**iDuna** is a complete wearable health monitoring platform consisting of two tightly integrated systems:

1. **iDuna Strap** — An ESP32-based wearable device with a MAX30102 PPG (photoplethysmography) sensor and a TFT LCD display. It performs real-time heart rate measurement using a multi-stage DSP pipeline, detects cardiac anomalies on-device, and transmits data wirelessly via BLE.

2. **iDuna App** — A Kotlin Android application built with Jetpack Compose that connects to the strap over BLE, displays live heart rate data on a rich dashboard, stores readings in a local Room database, triggers emergency SOS alerts with auto-call and auto-SMS, and generates shareable PDF health reports.

### Key Features

| Feature | Strap (ESP32) | App (Android) |
|---------|---------------|---------------|
| Heart rate measurement | ✅ On-device DSP | ✅ Live display |
| Anomaly detection | ✅ 5 types | ✅ Alert logging |
| Local display | ✅ TFT 240×240 | — |
| BLE communication | ✅ GATT Server | ✅ GATT Client |
| Data persistence | — | ✅ Room SQLite |
| Emergency SOS | — | ✅ Auto-call + SMS |
| PDF reports | — | ✅ Generate + Share |
| History analytics | — | ✅ Daily/Weekly/Monthly |
| Dark/Light theme | — | ✅ Toggle in settings |

---

## 2. System Architecture

The platform follows a three-tier architecture:

```
┌─────────────────────────────────────────────────────────┐
│                   iDuna WEARABLE STRAP                  │
│                                                         │
│  MAX30102 ──→ DSP Pipeline ──→ Beat Processing          │
│  (PPG Sensor)  (100 Hz)        (BPM + Avg)              │
│       │                            │                    │
│       │                     Anomaly Detection            │
│       │                     (5 types)                   │
│       │                            │                    │
│       ├─── TFT Display ◄──────────┤                    │
│       │    (240×240)               │                    │
│       └─── BLE GATT Server ◄──────┘                    │
│            (Service 0x180D)                             │
└─────────────────────┬───────────────────────────────────┘
                      │ BLE 4.2 Wireless
                      │ Notify @ 1 Hz
                      │ HR: [flags, BPM, anomalyCode]
                      │ Avg: [flags, avgBPM]
┌─────────────────────▼───────────────────────────────────┐
│                   iDuna ANDROID APP                     │
│                                                         │
│  BLE Manager ──→ Repository ──→ ViewModel ──→ UI        │
│  (Scan/Connect)  (Data Hub)    (StateFlow)  (Compose)   │
│       │               │                                 │
│       │          Room Database ◄── AlertNotifier         │
│       │          (4 tables)        (Notification)        │
│       │               │                                 │
│       │          PDF Generator                          │
│       │          (Health Reports)                       │
│       │               │                                 │
│       └────── Emergency SOS System                     │
│               (10s countdown → auto-call + SMS)         │
└─────────────────────────────────────────────────────────┘
```

---

## 3. Hardware Requirements

### Wearable Strap Components

| Component | Model | Purpose | Interface |
|-----------|-------|---------|-----------|
| Microcontroller | **ESP32** (any dev board) | Processing + BLE | — |
| PPG Sensor | **MAX30102** | Heart rate detection via infrared LED | I²C (SDA/SCL) |
| Display | **TFT SPI LCD 240×240** (ST7789 / ILI9341) | On-device UI | SPI |
| Power | 3.7V LiPo battery or USB | Power supply | — |

### Wiring Diagram

```
ESP32              MAX30102
─────              ────────
3.3V  ──────────── VIN
GND   ──────────── GND
GPIO 21 (SDA) ──── SDA
GPIO 22 (SCL) ──── SCL

ESP32              TFT Display (ST7789)
─────              ────────────────────
Configure pins in TFT_eSPI User_Setup.h
Typical: CS=5, DC=16, RST=17, MOSI=23, SCLK=18
```

### Android Phone Requirements

- Android 8.0+ (API 26)
- Bluetooth Low Energy support
- Location permission (for BLE scanning on API ≤ 30)

---

## 4. Firmware — ESP32 Strap

The firmware is a single Arduino `.ino` file running on the ESP32. It handles everything from sensor reading to signal processing to BLE transmission.

### 4.1 Libraries Required

| Library | Version | Purpose |
|---------|---------|---------|
| `Wire.h` | Built-in | I²C communication with MAX30102 |
| `BLEDevice.h` | Built-in (ESP32) | BLE GATT server |
| `MAX30105.h` | SparkFun MAX3010x | PPG sensor driver |
| `TFT_eSPI.h` | Bodmer TFT_eSPI | SPI display driver |

Install via Arduino Library Manager:
- **SparkFun MAX3010x Pulse and Proximity Sensor Library**
- **TFT_eSPI** by Bodmer

### 4.2 DSP Pipeline (Digital Signal Processing)

The firmware runs a 5-stage DSP pipeline at **100 Hz** to extract heart rate from the raw IR signal:

```
IR Raw Signal (100 Hz)
        │
        ▼
┌─── Stage 1: DC Removal ────────────────────────────────┐
│  Algorithm: Running mean subtraction                    │
│  Window: 200 samples (2 seconds)                       │
│  Purpose: Remove DC offset, keep only AC pulsatile     │
│           component of the PPG waveform                │
│  Function: removeDC(sample)                            │
└────────────────────────────────────────────────────────┘
        │
        ▼
┌─── Stage 2: Bandpass Filter ───────────────────────────┐
│  Algorithm: 2-stage cascaded Biquad IIR filter          │
│  Passband: 0.5 Hz – 4.0 Hz (30–240 BPM range)         │
│  Purpose: Isolate cardiac pulse frequency, reject      │
│           motion artifacts and high-frequency noise     │
│  Function: biquadFilter(sample)                        │
│  Coefficients: Pre-computed for 100 Hz sample rate     │
└────────────────────────────────────────────────────────┘
        │
        ▼
┌─── Stage 3: Normalisation ─────────────────────────────┐
│  Algorithm: Z-score normalisation                       │
│  Buffer: 60 samples                                    │
│  Purpose: Scale signal to unit variance so peak        │
│           detection threshold works across different   │
│           skin types and finger pressures              │
│  Function: normalise(sample)                           │
└────────────────────────────────────────────────────────┘
        │
        ▼
┌─── Stage 4: Peak Detection ───────────────────────────┐
│  Algorithm: Adaptive threshold with refractory period   │
│  Threshold: Starts at 0.5, adapts with α=0.1          │
│  Refractory: 300 ms minimum between peaks              │
│  Logic: Candidate must be > both neighbors AND         │
│         > adaptive threshold                           │
│  Purpose: Detect heartbeat peaks in the filtered       │
│           waveform                                     │
│  Function: detectPeak(nowMs)                           │
└────────────────────────────────────────────────────────┘
        │
        ▼
┌─── Stage 5: Beat Processing ──────────────────────────┐
│  a) IBI Validation: 250ms ≤ IBI ≤ 1500ms              │
│     (rejects impossible intervals: <40 or >240 BPM)    │
│  b) Outlier Rejection: Median ±20 BPM                 │
│     (sorts last N IBIs, rejects if deviation > 20)     │
│  c) Weighted Average: 12-beat ring buffer              │
│     (recent beats weighted more heavily)               │
│  d) Rolling Average: 10-beat simple mean               │
│     (for the "Avg HR" display value)                   │
│  Functions: validateIBI(), isOutlier(),                │
│             weightedAvgBPM(), addToAvgHistory()        │
└────────────────────────────────────────────────────────┘
```

### 4.3 Anomaly Detection Engine

The firmware evaluates 5 types of cardiac anomalies. Each is encoded as a **bitmask** and transmitted to the app:

| Bit | Hex Code | Anomaly | Detection Rule | Condition |
|-----|----------|---------|----------------|-----------|
| 0 | `0x01` | **Tachycardia** | `currentBPM > 100` | Sustained for > 10 seconds |
| 1 | `0x02` | **Bradycardia** | `currentBPM < 50` | Sustained for > 10 seconds |
| 2 | `0x04` | **Irregular Rhythm** | `IBI_StdDev > 150ms` | High beat-to-beat variability |
| 3 | `0x08` | **Missed Beat / Pause** | `last_IBI > 1500ms` | Abnormally long gap between beats |
| 4 | `0x10` | **High Variance** | `|ΔBPM| > 20` | Sudden large BPM jump |

Multiple anomalies can be active simultaneously (bitwise OR). For example, `0x05` means both Tachycardia (`0x01`) AND Irregular Rhythm (`0x04`) are detected.

### 4.4 Finger Detection

The firmware uses IR reflectance levels with hysteresis to detect finger placement:

- **Finger ON**: IR raw > 50,000 for ≥ 500ms (debounced)
- **Finger OFF**: IR raw < 30,000 (instant removal)
- On finger removal: full DSP pipeline reset, anomaly flags cleared
- On finger placement: DSP pipeline restarted, 60-sample warmup period

### 4.5 TFT Display Layout

The 240×240 pixel display is divided into zones:

```
┌────────────────────────────────────┐
│ [♥ iDuna]              (0,0–28)   │  ← Header bar (dark blue bg)
├────────────────────────────────────┤
│           HEART RATE               │  ← Label
│                                    │
│            ██  ██                  │  ← Large BPM value (size 6 font)
│            ██  ██                  │    or "Place Finger" / "Warmup..."
│                                    │
│              BPM                   │  ← Unit label
├────────────────────────────────────┤
│ AVG        74 BPM         10-beat  │  ← Average HR bar
├────────────────────────────────────┤
│ ▲ TACHYCARDIA                ███  │  ← Anomaly zone (color-coded)
│ ! HR > 100 BPM  >10s        ███  │    Green = Normal
│                              ███  │    Orange/Red/Purple = Anomaly
├────────────────────────────────────┤
│ 🔴 Finger on           BLE:ON    │  ← Status bar
└────────────────────────────────────┘
```

### 4.6 BLE GATT Server Configuration

```
Service: Heart Rate Service (0x180D)
├── Characteristic: Heart Rate Measurement (0x2A37)
│   ├── Properties: READ | NOTIFY
│   ├── Descriptor: CCCD (0x2902)
│   └── Value: [flags=0x00, BPM, anomalyCode]  (3 bytes)
│
└── Characteristic: Average Heart Rate (0x2A39)
    ├── Properties: READ | NOTIFY
    ├── Descriptor: CCCD (0x2902)
    └── Value: [flags=0x00, avgBPM]  (2 bytes)

Advertising:
  - Device name: "iDuna"
  - Service UUID advertised: 0x180D
  - Scan response: enabled
  - Preferred connection interval: 0x06–0x12
  - Auto-restart advertising on disconnect
```

### 4.7 Firmware Loop Timing

| Event | Frequency | Purpose |
|-------|-----------|---------|
| Sensor sample | 100 Hz (10ms) | Read IR, run DSP pipeline |
| Status print | 1 Hz (1000ms) | Serial debug output |
| Display update | 1 Hz (1000ms) | Refresh TFT screen |
| BLE notify | 1 Hz (1000ms) | Send HR + Avg to app |
| Anomaly check | Every 5th beat | Evaluate cardiac anomalies |
| Finger check | 100 Hz | Debounced presence detection |

---

## 5. Android App — iDuna Mobile

### 5.1 Architecture: MVVM

```
┌─────────────┐    ┌───────────────────┐    ┌────────────────┐
│   UI Layer  │◄───│   IdunaViewModel  │◄───│ IdunaRepository│
│  (Compose)  │    │   (StateFlow)     │    │  (Data Hub)    │
│  10 Screens │───►│   User Actions    │───►│                │
└─────────────┘    └───────────────────┘    └───────┬────────┘
                                                    │
                           ┌────────────────────────┼──────────┐
                           │                        │          │
                    ┌──────▼──────┐   ┌─────────────▼───┐  ┌──▼──────────┐
                    │ BleHeart    │   │  Room Database   │  │ AlertNotif  │
                    │ RateManager │   │  (iduna.db)      │  │ + PDF Gen   │
                    │ (GATT)      │   │  4 tables        │  │             │
                    └─────────────┘   └─────────────────┘  └─────────────┘
```

**Data flows unidirectionally** using Kotlin `StateFlow`:
- `BleHeartRateManager` → emits `BleReadingPacket` via `StateFlow`
- `IdunaRepository` → collects packets, inserts to Room, combines into `DashboardUiState`
- `IdunaViewModel` → exposes `StateFlow<DashboardUiState>` to UI
- Compose screens → `collectAsStateWithLifecycle()` to observe

### 5.2 App Screens (10 total)

| # | Screen | Description |
|---|--------|-------------|
| 1 | **SplashScreen** | Animated pulsing orb with iDuna logo, 1.8s auto-navigate |
| 2 | **DeviceConnectionScreen** | BLE permission handling, scan/stop/reconnect buttons, permission status |
| 3 | **DashboardScreen** | Live BPM with animated orb, average BPM, anomaly status, live 60s graph, breathing focus widget, quick action shortcuts |
| 4 | **GraphScreen** | Full-screen live line chart colored by anomaly type |
| 5 | **HistoryScreen** | Daily/Weekly/Monthly filter chips, avg/max/min metric pills, scrollable reading list |
| 6 | **AlertsScreen** | Chronological anomaly event timeline with color-coded cards |
| 7 | **EmergencySosScreen** | 10-second countdown ring, "Call Now" + "Send SMS" buttons, auto-trigger on expiry, flashing red background |
| 8 | **ReportsScreen** | Date picker, summary preview (avg/max/min), chart preview, PDF generation + share |
| 9 | **ProfileScreen** | Name, age, emergency contact number, save to Room |
| 10 | **SettingsScreen** | 5 toggle switches: Notifications, Vibration, Auto SOS, BLE Auto Connect, Dark Mode |

### 5.3 Navigation Flow

```
App Launch
    │
    ▼
SplashScreen (1.8s)
    │
    ▼
DeviceConnectionScreen
    │ (auto-navigate on BLE connected)
    ▼
MainShell (Drawer Navigation)
    ├── Dashboard (default)
    ├── Live Graph
    ├── History
    ├── Alerts
    ├── Reports
    ├── Profile
    ├── Settings
    └── [Disconnect → back to Connection Screen]

    At any time, if anomaly persists > 10 seconds:
    ──→ EmergencySosScreen overlays automatically
        ├── Cancel ("I'm okay")
        └── Countdown expires → Auto SMS + Auto Call
```

### 5.4 Room Database Schema

**Database name:** `iduna.db` (version 1)

**Table: `heart_rate_readings`**
| Column | Type | Description |
|--------|------|-------------|
| id | LONG (PK, auto) | Auto-increment primary key |
| timestamp | LONG | Unix epoch milliseconds |
| bpm | INT | Heart rate in BPM |
| averageBpm | INT | 10-beat rolling average |
| anomalyCode | INT | Bitmask (0x00–0x1F) |
| fingerDetected | BOOLEAN | Finger placement status |

**Table: `alert_events`**
| Column | Type | Description |
|--------|------|-------------|
| id | LONG (PK, auto) | Auto-increment primary key |
| timestamp | LONG | When the anomaly was detected |
| bpm | INT | BPM at time of anomaly |
| anomalyCode | INT | Which anomaly type |
| message | STRING | Human-readable description |

**Table: `user_profile`**
| Column | Type | Description |
|--------|------|-------------|
| id | INT (PK, fixed=0) | Single-row table |
| name | STRING | Patient name |
| age | INT | Patient age |
| emergencyContact | STRING | Phone number for SOS |

**Table: `user_settings`**
| Column | Type | Description |
|--------|------|-------------|
| id | INT (PK, fixed=0) | Single-row table |
| notificationsEnabled | BOOLEAN | Push notifications toggle |
| vibrationEnabled | BOOLEAN | Vibration on anomaly |
| autoSosEnabled | BOOLEAN | Auto-trigger SOS countdown |
| bleAutoConnectEnabled | BOOLEAN | Auto-reconnect on disconnect |
| darkModeEnabled | BOOLEAN | Dark/Light theme toggle |

### 5.5 Emergency SOS System

The SOS system is a multi-layered safety feature:

1. **Trigger**: Repository detects same anomaly type sustained for ≥ 10 seconds
2. **Navigation**: App auto-navigates to `EmergencySosScreen`
3. **Countdown**: 10-second visual countdown ring
4. **User can cancel**: "I'm okay — Cancel SOS" button
5. **Auto-SMS**: `SmsManager.sendTextMessage()` to emergency contact with anomaly details
6. **Auto-Call**: `ACTION_CALL` intent to emergency contact after 500ms delay
7. **Permission handling**: Runtime permission requests for `CALL_PHONE` and `SEND_SMS`
8. **Fallback**: If programmatic SMS fails, opens SMS app with pre-filled message
9. **Suppression**: After cancellation, SOS won't re-trigger until a normal reading resets the state

**SOS SMS message format:**
```
iDuna Emergency Alert: Tachycardia detected at 120 BPM.
Please check on this patient immediately.
```

### 5.6 PDF Report Generation

- **Engine**: Android `PdfDocument` API (no external libraries)
- **Page size**: 1080 × 1440 pixels
- **Design**: Dark background (#090B12), cyan headings, gray body text
- **Content**: Patient info, vitals summary (avg/max/min), anomaly counts, mini HR trend chart
- **Output**: Saved to `app_files/reports/iduna_report_YYYYMMDD_HHMM.pdf`
- **Sharing**: Via `FileProvider` + `ACTION_SEND` intent

---

## 6. BLE Communication Protocol

### 6.1 Shared UUIDs (Must Match Exactly)

| UUID | Name | Used In |
|------|------|---------|
| `0000180D-0000-1000-8000-00805F9B34FB` | Heart Rate Service | Both |
| `00002A37-0000-1000-8000-00805F9B34FB` | HR Measurement Characteristic | Both |
| `00002A39-0000-1000-8000-00805F9B34FB` | Average HR Characteristic | Both |
| `00002902-0000-1000-8000-00805F9B34FB` | CCCD Descriptor | Both |

### 6.2 Packet Format

**HR Characteristic (0x2A37) — Notified every 1 second:**
```
Byte 0: Flags    = 0x00 (8-bit BPM format)
Byte 1: BPM      = 0–255 (current heart rate)
Byte 2: Anomaly  = Bitmask (0x00=normal, 0x01=tachy, 0x02=brady, etc.)
```

**Avg HR Characteristic (0x2A39) — Notified every 1 second:**
```
Byte 0: Flags    = 0x00
Byte 1: Avg BPM  = 0–255 (10-beat rolling average)
```

### 6.3 Connection Sequence

```
1. ESP32 boots → starts BLE advertising as "iDuna"
2. Android app scans for device name "iDuna" OR service UUID 0x180D
3. App calls connectGatt(TRANSPORT_LE)
4. ESP32 stops advertising, fires onConnect callback
5. App discovers services → finds 0x180D
6. App enables CCCD notification on 0x2A37
7. App enables CCCD notification on 0x2A39 (if NOTIFY supported) or reads it
8. Every 1 second (when finger on + BPM > 0 + ≥3 beats):
   - ESP32 notifies 0x2A37 with [0x00, BPM, anomalyCode]
   - ESP32 notifies 0x2A39 with [0x00, avgBPM]
9. App parses bytes → BleReadingPacket → Repository → Room + UI
10. On disconnect: ESP32 restarts advertising, App schedules 3s reconnect
```

---

## 7. Setup & Installation

### 7.1 Firmware Setup (Arduino IDE)

**Step 1: Install Arduino IDE**
- Download from https://www.arduino.cc/en/software

**Step 2: Add ESP32 Board Support**
- Go to File → Preferences → Additional Board Manager URLs
- Add: `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
- Go to Tools → Board → Board Manager → Search "ESP32" → Install

**Step 3: Install Libraries**
- Sketch → Include Library → Manage Libraries
- Search and install:
  - `SparkFun MAX3010x Pulse and Proximity Sensor Library`
  - `TFT_eSPI` by Bodmer

**Step 4: Configure TFT_eSPI**
- Navigate to the TFT_eSPI library folder
- Edit `User_Setup.h` to match your display and ESP32 pin configuration
- Set the correct driver (ST7789 or ILI9341) and SPI pins

**Step 5: Wire the Hardware**
- Connect MAX30102 to ESP32 via I²C (SDA=21, SCL=22)
- Connect TFT display via SPI (as per your `User_Setup.h`)
- Power via USB or 3.7V LiPo

**Step 6: Upload Firmware**
- Open the `.ino` file in Arduino IDE
- Select board: "ESP32 Dev Module"
- Select port: your ESP32's COM port
- Click Upload
- Open Serial Monitor at 115200 baud to verify output

**Expected Serial Output:**
```
=== iDuna ===
[INIT] MAX30102 OK
[BLE] Advertising as 'iDuna'
[WAIT] Place finger on sensor

STATUS        | HR  | AVG | ANOM
--------------|-----|-----|-----
[FINGER] Detected — measuring...
[ADVERTISING] | 72  | 71
[CONNECTED]   | 74  | 72
[TX] HR=74 Avg=72 Anom=0x0
```

### 7.2 Android App Setup (Android Studio)

**Step 1: Install Android Studio**
- Download from https://developer.android.com/studio

**Step 2: Open Project**
- File → Open → Navigate to `d:\FInalyear project\iDuna`
- Wait for Gradle sync to complete (may take a few minutes on first open)

**Step 3: Verify Build**
- Build → Make Project (Ctrl+F9)
- Ensure no compilation errors

**Step 4: Run on Device**
- Connect an Android phone (API 26+) via USB
- Enable Developer Options and USB Debugging on the phone
- Click Run (Shift+F10) → Select your device
- **Note:** BLE features require a physical device — the emulator does not support BLE

**Step 5: Grant Permissions**
- The app will request Bluetooth and Location permissions on the Connection screen
- Grant all permissions for full functionality
- For SOS: Grant CALL_PHONE and SEND_SMS when prompted

### 7.3 Testing End-to-End

1. Power on the ESP32 strap — TFT should show "iDuna Strap v1.0"
2. Open the iDuna app on your phone
3. Tap "Start Scan" on the Connection screen
4. The app will find "iDuna" and auto-connect
5. Once connected, the app navigates to the Dashboard
6. Place your finger on the MAX30102 sensor
7. After ~2 seconds warmup, live BPM appears on both the TFT and the app
8. The live graph on the Dashboard starts plotting in real-time

---

## 8. How It Works — End-to-End Flow

### Complete Data Journey (Sensor to Screen)

```
1. MAX30102 emits infrared light → reflects off blood → photodiode captures
2. ESP32 reads IR value via I²C at 100 Hz
3. Finger detection: IR > 50,000 → finger present (debounced 500ms)
4. DC Removal: Subtract running mean (200-sample window)
5. Bandpass Filter: 2-stage Biquad IIR isolates 0.5–4 Hz cardiac band
6. Z-Score Normalisation: Scale to unit variance (60-sample buffer)
7. Peak Detection: Adaptive threshold identifies heartbeat peaks
8. IBI Calculation: Time between consecutive peaks
9. IBI Validation: Reject if < 250ms or > 1500ms
10. Outlier Rejection: Reject if > 20 BPM from recent median
11. Weighted Average: Compute BPM from 12-beat recency-weighted buffer
12. Rolling Average: Simple 10-beat mean for "Avg HR"
13. Anomaly Check: Evaluate 5 anomaly types every 5th beat
14. TFT Display: Update BPM, Average, Anomaly zone, Status bar
15. BLE Notify: Send [0x00, BPM, anomalyCode] + [0x00, avgBPM] every 1s
──── BLE 4.2 wireless link ────
16. BleHeartRateManager: Receives GATT notification
17. parseCharacteristic(): Extract BPM, anomalyCode from bytes
18. Emit BleReadingPacket via StateFlow
19. IdunaRepository.handleIncomingPacket():
    a. Create HeartRateSample
    b. Insert to Room database
    c. Add to liveReadings buffer (60s window, max 180)
    d. Check for anomaly change → create AlertEvent + send notification
    e. Check SOS trigger (same anomaly > 10s → SosUiState)
20. DashboardUiState: Combined from connection, current reading, live buffer
21. IdunaViewModel: Exposes all state as StateFlow
22. Compose UI: collectAsStateWithLifecycle() → re-render on change
23. If SOS triggered: Auto-navigate to EmergencySosScreen
24. Countdown 10s → Auto SMS + Auto Call to emergency contact
```

---

## 9. Project Structure

```
iDuna/
├── README.md                          ← This file
├── system_architecture_diagram.html   ← Visual architecture diagram
├── build.gradle.kts                   ← Project-level Gradle
├── settings.gradle.kts
├── gradle.properties
│
├── firmware/
│   └── iDuna.ino                      ← ESP32 firmware (upload via Arduino IDE)
│
└── app/
    ├── build.gradle.kts               ← App-level Gradle (dependencies)
    └── src/main/
        ├── AndroidManifest.xml        ← Permissions, FileProvider, Activity
        ├── res/
        │   ├── drawable/
        │   │   ├── ic_iduna_logo.xml  ← Vector logo
        │   │   └── ic_launcher_foreground.xml
        │   ├── mipmap-anydpi-v26/
        │   │   ├── ic_launcher.xml
        │   │   └── ic_launcher_round.xml
        │   ├── values/
        │   │   ├── colors.xml
        │   │   ├── strings.xml
        │   │   └── themes.xml
        │   └── xml/
        │       ├── backup_rules.xml
        │       ├── data_extraction_rules.xml
        │       └── file_paths.xml     ← For PDF FileProvider
        │
        └── java/com/iduna/
            ├── IdunaApplication.kt    ← Application subclass
            ├── AppContainer.kt        ← Manual dependency injection
            ├── MainActivity.kt        ← Single activity host
            ├── IdunaViewModel.kt      ← MVVM ViewModel
            │
            ├── data/
            │   ├── ble/
            │   │   └── BleHeartRateManager.kt  ← GATT scan/connect/parse
            │   ├── local/
            │   │   ├── IdunaDatabase.kt        ← Room DB definition
            │   │   ├── dao/
            │   │   │   └── AppDaos.kt          ← 4 DAOs
            │   │   └── entity/
            │   │       └── Entities.kt         ← 4 Room entities
            │   └── repository/
            │       └── IdunaRepository.kt      ← Central data orchestrator
            │
            ├── domain/
            │   └── model/
            │       └── AppModels.kt            ← 11 domain models & enums
            │
            ├── ui/
            │   ├── components/
            │   │   └── AppComponents.kt        ← 12 reusable composables
            │   ├── navigation/
            │   │   └── IdunaNavGraph.kt        ← Root + drawer navigation
            │   ├── screens/
            │   │   ├── SplashScreen.kt
            │   │   ├── DeviceConnectionScreen.kt
            │   │   ├── DashboardScreen.kt
            │   │   ├── GraphScreen.kt
            │   │   ├── HistoryScreen.kt
            │   │   ├── AlertsScreen.kt
            │   │   ├── EmergencySosScreen.kt
            │   │   ├── ReportsScreen.kt
            │   │   ├── ProfileScreen.kt
            │   │   └── SettingsScreen.kt
            │   └── theme/
            │       └── Theme.kt               ← Material3 dark/light
            │
            └── util/
                ├── AlertNotifier.kt           ← Notification + vibration
                ├── PdfReportGenerator.kt      ← PDF health report
                └── TimeFormatters.kt          ← Date/time utilities
```

### Code Statistics

| Metric | Value |
|--------|-------|
| Total Kotlin files | 26 |
| Total Kotlin LOC | 3,269 |
| Firmware LOC | ~380 |
| UI Screens | 10 |
| Reusable Components | 12 |
| Room Tables | 4 |
| Room DAOs | 4 |
| Domain Models | 11 |
| BLE Characteristics | 2 |
| Anomaly Types | 5 (firmware) / 6 (app enum) |

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Sensor | MAX30102 PPG (I²C) |
| MCU | ESP32 (Arduino framework) |
| Display | TFT SPI 240×240 (TFT_eSPI) |
| Wireless | BLE 4.2 (ESP32 stack) |
| Language | Kotlin (JVM 17) |
| UI | Jetpack Compose (BOM 2024.10.01) |
| Design | Material 3 (Dark + Light) |
| Architecture | MVVM (ViewModel + Repository + StateFlow) |
| Database | Room 2.6.1 (SQLite) |
| Navigation | Navigation Compose 2.8.4 |
| Concurrency | Kotlin Coroutines 1.9.0 |
| Build | Gradle KTS (compileSdk 35, minSdk 26) |
| Reports | Android PdfDocument |

---

## 10. Troubleshooting

| Problem | Cause | Solution |
|---------|-------|----------|
| TFT displays "MAX30102 ERROR" | Sensor not wired or I²C address mismatch | Check SDA/SCL wiring, ensure 3.3V power |
| App can't find "iDuna" during scan | BLE not advertising, phone BT off | Restart ESP32, ensure phone Bluetooth is on |
| App connects but no BPM shows | Finger not on sensor | Place fingertip firmly on MAX30102 |
| BPM reads 0 for long time | DSP warmup period | Wait 2–3 seconds after placing finger |
| GATT error 133 on connect | Android BLE stack issue | Restart phone Bluetooth, try again |
| SOS doesn't trigger | Auto SOS disabled in settings | Check Settings → Auto SOS toggle |
| PDF share fails | FileProvider misconfigured | Ensure `file_paths.xml` is present in `res/xml/` |
| Permissions denied | User rejected runtime permission | Go to Settings → App → iDuna → Permissions → Allow |
| Build fails in Android Studio | Gradle sync needed | File → Sync Project with Gradle Files |
| Display shows wrong rotation | TFT_eSPI config mismatch | Adjust `tft.setRotation()` value (0–3) |

---
## iDuna Project Demo
<h2 align="center">🎥 iDuna Strap Project Demo</h2>

<p align="center">
  <video src="https://github.com/user-attachments/assets/a570b0d7-7cee-4c61-9556-3caa76a79974" controls width="600"/>
</p>

<h2 align="center">🎥 iDuna App Project Demo</h2>

<p align="center">
  <video src="https://github.com/user-attachments/assets/8a9f6e0f-6521-428f-a552-ead790dd2139" controls width="600"/>
</p>




> **Built with ❤️ by the iDuna Team — Final Year Project 2026**

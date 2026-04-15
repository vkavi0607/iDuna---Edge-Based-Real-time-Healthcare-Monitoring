package com.iduna.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import com.iduna.domain.model.AnomalyType
import com.iduna.domain.model.BleConnectionState
import com.iduna.domain.model.BleReadingPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class BleHeartRateManager(
    private val context: Context,
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var reconnectJob: Job? = null
    private var scanTimeoutJob: Job? = null
    private var lastDevice: BluetoothDevice? = null
    private var lastAverageBpm: Int = 0
    private var autoReconnectEnabled: Boolean = true
    private var isConnecting: Boolean = false

    private val _connectionState = MutableStateFlow(
        if (bluetoothAdapter == null) {
            BleConnectionState.BluetoothUnavailable
        } else {
            BleConnectionState.Idle
        },
    )
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _latestPacket = MutableStateFlow<BleReadingPacket?>(null)
    val latestPacket: StateFlow<BleReadingPacket?> = _latestPacket.asStateFlow()

    fun setAutoReconnect(enabled: Boolean) {
        autoReconnectEnabled = enabled
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startScan() {
        val adapter = bluetoothAdapter ?: run {
            _connectionState.value = BleConnectionState.BluetoothUnavailable
            return
        }
        if (!adapter.isEnabled) {
            _connectionState.value = BleConnectionState.BluetoothDisabled
            return
        }
        if (_connectionState.value == BleConnectionState.Scanning) return

        _connectionState.value = BleConnectionState.Scanning
        adapter.bluetoothLeScanner?.startScan(scanCallback)

        // Auto-stop scan after timeout to prevent battery drain
        scanTimeoutJob?.cancel()
        scanTimeoutJob = appScope.launch {
            delay(SCAN_TIMEOUT_MS)
            if (_connectionState.value == BleConnectionState.Scanning) {
                stopScan()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanTimeoutJob?.cancel()
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (_connectionState.value == BleConnectionState.Scanning) {
            _connectionState.value = BleConnectionState.Idle
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToLastDevice() {
        lastDevice?.let(::connect)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        reconnectJob?.cancel()
        scanTimeoutJob?.cancel()
        isConnecting = false
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = BleConnectionState.Disconnected
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        if (isConnecting || _connectionState.value == BleConnectionState.Connected) return
        stopScan()
        lastDevice = device
        isConnecting = true
        _connectionState.value = BleConnectionState.Connecting
        bluetoothGatt?.close()
        // autoConnect=false for faster initial connect; use TRANSPORT_LE to avoid transport errors
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceName = result.device.name ?: result.scanRecord?.deviceName
            val advertisedServices = result.scanRecord?.serviceUuids.orEmpty()
            val hasExpectedService = advertisedServices.any { it.uuid == BleUuids.SERVICE_UUID }
            val matchesName = deviceName?.contains(DEVICE_NAME, ignoreCase = true) == true
            if (matchesName || hasExpectedService) {
                connect(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = BleConnectionState.Error
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                // Non-success statuses (e.g. 133 = GATT_ERROR, 8 = GATT_CONN_TIMEOUT) must be
                // handled explicitly — they do NOT change newState to DISCONNECTED.
                isConnecting = false
                _connectionState.value = BleConnectionState.Disconnected
                gatt.close()
                bluetoothGatt = null
                scheduleReconnect()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isConnecting = false
                    _connectionState.value = BleConnectionState.Connected
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    isConnecting = false
                    _connectionState.value = BleConnectionState.Disconnected
                    gatt.close()
                    bluetoothGatt = null
                    scheduleReconnect()
                }

                else -> Unit
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = BleConnectionState.Error
                return
            }
            val service = gatt.getService(BleUuids.SERVICE_UUID) ?: run {
                // Service UUID not found — wrong device or firmware not advertising correctly
                _connectionState.value = BleConnectionState.Error
                return
            }
            enableNotification(gatt, service, BleUuids.HEART_RATE_CHARACTERISTIC_UUID)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                @Suppress("DEPRECATION")
                parseCharacteristic(characteristic, characteristic.value)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                parseCharacteristic(characteristic, value)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val service = gatt.getService(BleUuids.SERVICE_UUID) ?: return
            if (descriptor.characteristic.uuid == BleUuids.HEART_RATE_CHARACTERISTIC_UUID) {
                readOrEnableAverage(gatt, service)
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            parseCharacteristic(characteristic, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            parseCharacteristic(characteristic, value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotification(
        gatt: BluetoothGatt,
        service: BluetoothGattService,
        characteristicUuid: UUID,
    ) {
        val characteristic = service.getCharacteristic(characteristicUuid) ?: return
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(BleUuids.CCCD_UUID) ?: return

        // descriptor.value setter is deprecated and ignored on API 33+.
        // Must use writeDescriptor(descriptor, value) on API 33+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    @SuppressLint("MissingPermission")
    private fun readOrEnableAverage(
        gatt: BluetoothGatt,
        service: BluetoothGattService,
    ) {
        val characteristic = service.getCharacteristic(BleUuids.AVERAGE_CHARACTERISTIC_UUID) ?: return
        val hasNotify = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        val hasRead = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0

        when {
            hasNotify -> enableNotification(gatt, service, BleUuids.AVERAGE_CHARACTERISTIC_UUID)
            hasRead -> gatt.readCharacteristic(characteristic)
        }
    }

    private fun parseCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        when (characteristic.uuid) {
            BleUuids.HEART_RATE_CHARACTERISTIC_UUID -> {
                if (value.size < 2) return
                val flag = value[0].toInt() and 0xFF
                val isUInt16 = flag and 0x01 != 0
                val bpm = if (isUInt16 && value.size >= 3) {
                    ((value[2].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
                } else {
                    value[1].toInt() and 0xFF
                }
                // Byte index after the BPM field
                val extraIndex = if (isUInt16) 3 else 2

                // Anomaly code byte — sent by iDuna firmware after the BPM
                val anomalyCode = if (value.size > extraIndex) {
                    value[extraIndex].toInt() and 0xFF
                } else {
                    0
                }

                // Finger-detected byte — bit 0 of the next byte after anomaly code
                val fingerIndex = extraIndex + 1
                val fingerDetected = if (value.size > fingerIndex) {
                    value[fingerIndex].toInt() and 0x01 != 0
                } else {
                    true // assume detected if firmware doesn't include the byte
                }

                _latestPacket.value = BleReadingPacket(
                    bpm = bpm,
                    averageBpm = lastAverageBpm,
                    anomalyType = AnomalyType.fromCode(anomalyCode),
                    fingerDetected = fingerDetected,
                    timestamp = System.currentTimeMillis(),
                )
            }

            BleUuids.AVERAGE_CHARACTERISTIC_UUID -> {
                if (value.isEmpty()) return
                lastAverageBpm = if (value.size >= 2) {
                    value[1].toInt() and 0xFF
                } else {
                    value[0].toInt() and 0xFF
                }
                _latestPacket.value = _latestPacket.value?.copy(averageBpm = lastAverageBpm)
            }
        }
    }

    private fun scheduleReconnect() {
        if (!autoReconnectEnabled || lastDevice == null) return
        reconnectJob?.cancel()
        reconnectJob = appScope.launch {
            delay(RECONNECT_DELAY_MS)
            connectToLastDevice()
        }
    }

    private object BleUuids {
        // These UUIDs match the expected iDuna wearable firmware contract.
        // Standard Heart Rate service (0x180D) + Measurement characteristic (0x2A37)
        val SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
        val HEART_RATE_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
        val AVERAGE_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("00002A39-0000-1000-8000-00805F9B34FB")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }

    companion object {
        const val DEVICE_NAME = "iDuna"
        private const val RECONNECT_DELAY_MS = 3_000L
        private const val SCAN_TIMEOUT_MS = 15_000L
    }
}

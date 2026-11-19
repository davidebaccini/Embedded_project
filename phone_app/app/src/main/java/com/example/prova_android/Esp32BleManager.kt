package com.example.prova_android

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

class Esp32BleManager(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    var statusText by mutableStateOf("Disconnected")
        private set

    var isConnected by mutableStateOf(false)
        private set

    var isScanning by mutableStateOf(false)
        private set

    var fitnessData by mutableStateOf(FitnessData())
        private set

    var lastMessage by mutableStateOf("")
        private set

    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val serviceUuid = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val charUuid = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null

    @Volatile
    private var isConnecting = false

    private val scanTimeoutRunnable = Runnable {
        if (isScanning) {
            stopScan()
            statusText = "ESP32 not found (scan timed out)"
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    fun onPermissionsDenied() {
        runOnMain {
            statusText = "Bluetooth permissions are required to scan and connect"
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanAndConnect() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            runOnMain { statusText = "Bluetooth is turned off or unavailable" }
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            runOnMain { statusText = "BLE scanner is unavailable" }
            return
        }
        if (isScanning || isConnecting || isConnected) {
            return
        }

        runOnMain {
            isScanning = true
            statusText = "Scanning for ESP32..."
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        mainHandler.removeCallbacks(scanTimeoutRunnable)
        mainHandler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT_MS)
        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        mainHandler.removeCallbacks(scanTimeoutRunnable)
        if (isScanning) {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            runOnMain {
                isScanning = false
                if (!isConnected && !isConnecting) {
                    statusText = "Scan stopped"
                }
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (isConnecting) return
            isConnecting = true

            mainHandler.removeCallbacks(scanTimeoutRunnable)
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)

            val deviceLabel = result.device.name ?: result.device.address
            runOnMain {
                isScanning = false
                statusText = "Found $deviceLabel, connecting..."
            }

            bluetoothGatt?.close()
            bluetoothGatt = result.device.connectGatt(
                appContext,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        }

        override fun onScanFailed(errorCode: Int) {
            mainHandler.removeCallbacks(scanTimeoutRunnable)
            isConnecting = false
            runOnMain {
                isScanning = false
                statusText = "Scan failed (error $errorCode)"
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                runOnMain { statusText = "Connected, discovering services..." }
                if (!gatt.discoverServices()) {
                    runOnMain { statusText = "Failed to start service discovery" }
                    gatt.disconnect()
                }
            } else {
                val errorSuffix =
                    if (status != BluetoothGatt.GATT_SUCCESS) " (status $status)" else ""
                isConnecting = false
                gatt.close()
                if (bluetoothGatt === gatt) {
                    bluetoothGatt = null
                }
                runOnMain {
                    isConnected = false
                    statusText = "Disconnected$errorSuffix"
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            isConnecting = false
            if (status != BluetoothGatt.GATT_SUCCESS) {
                runOnMain { statusText = "Service discovery failed (status $status)" }
                gatt.disconnect()
                return
            }

            val characteristic = gatt.getService(serviceUuid)?.getCharacteristic(charUuid)
            if (characteristic == null) {
                runOnMain { statusText = "ESP32 service/characteristic not found" }
                gatt.disconnect()
                return
            }

            val notifyEnabled = gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(cccdUuid)
            if (notifyEnabled && descriptor != null) {
                val cccdValue =
                    if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    } else if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    } else {
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    }

                runOnMain {
                    isConnected = true
                    statusText = "Connected, enabling notifications..."
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, cccdValue)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = cccdValue
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
            } else {
                runOnMain {
                    isConnected = true
                    statusText = "Connected (notifications unavailable: missing CCCD)"
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == cccdUuid) {
                runOnMain {
                    statusText = if (status == BluetoothGatt.GATT_SUCCESS) {
                        "Connected & Listening..."
                    } else {
                        "Failed to enable notifications (status $status)"
                    }
                }
            }
        }

        // Android 13+ (API 33+)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingMessage(value.toString(Charsets.UTF_8))
        }

        // Android 12 and older
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val bytes = characteristic.value ?: return
            handleIncomingMessage(bytes.toString(Charsets.UTF_8))
        }
    }

    private fun handleIncomingMessage(rawMessage: String) {
        val trimmed = rawMessage.trim()
        if (trimmed.isEmpty()) return
        runOnMain {
            lastMessage = trimmed
            fitnessData = parseBleMessage(fitnessData, trimmed)
        }
    }

    @SuppressLint("MissingPermission")
    fun sendText(message: String) {
        val gatt = bluetoothGatt
        val characteristic = gatt?.getService(serviceUuid)?.getCharacteristic(charUuid)
        if (gatt == null || characteristic == null) {
            runOnMain { statusText = "Cannot send: not connected" }
            return
        }

        val payload = message.toByteArray(Charsets.UTF_8)
        val writeType =
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, payload, writeType)
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
        runOnMain { statusText = "Sent: $message" }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        isConnecting = false
        bluetoothGatt?.disconnect()
    }

    @SuppressLint("MissingPermission")
    fun close() {
        stopScan()
        isConnecting = false
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        runOnMain {
            isConnected = false
            statusText = "Disconnected"
        }
    }

    companion object {
        private const val SCAN_TIMEOUT_MS = 10_000L
    }
}

package com.example.app.ble // 실제 패키지명으로 변경하세요

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID

@SuppressLint("MissingPermission") // Permissions should be checked before calling connect/read etc.
object BleConnectionManager {
    private const val TAG = "BleConnectionManager"

    private var bluetoothGatt: BluetoothGatt? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var applicationContext: Context? = null // 애플리케이션 컨텍스트 저장

    var connectedDevice: BluetoothDevice? = null
        private set
    var connectionState: Int = BluetoothProfile.STATE_DISCONNECTED
        private set

    // 리스너 인터페이스 정의
    interface BleConnectionListener {
        fun onConnectionStateChanged(newState: Int, gatt: BluetoothGatt?)
        fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int)
        fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, value: ByteArray?, status: Int)
        fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int)
        fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, value: ByteArray?)
        fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int)
    }

    private val listeners = mutableSetOf<BleConnectionListener>() // 중복 방지를 위해 Set 사용
    private val mainHandler = Handler(Looper.getMainLooper())

    // --- 주요 UUID 설정 (아두이노 스케치에 맞춰 수정) ---
    // 제공해주신 아두이노 코드의 UUID로 교체합니다.

    // 서비스 UUID
    val SENSOR_SERVICE_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-56789abcde00")

    // 특성 UUID
    // 아두이노 → 앱 (문자열 알림/데이터) - BLERead | BLENotify
    val ACCEL_TEXT_CHAR_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-56789abcde02")
    // 앱 → 아두이노 (명령/응답) - BLEWrite | BLEWriteWithoutResponse
    val CMD_CHAR_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-56789abcde10")

    // 일반적으로 많이 사용되는 Client Characteristic Configuration Descriptor (CCCD) UUID
    // 이 값은 표준이므로 보통 변경할 필요가 없습니다.
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")


    // 현재 앱에서 사용할 목표 특성 UUID
    // 안드로이드 앱의 주요 상호작용에 따라 이 부분을 설정합니다.
    var TARGET_SERVICE_UUID: UUID = SENSOR_SERVICE_UUID // 사용할 서비스

    // 읽기 요청 대상 특성: accelTextChar (BLERead 속성이 있으므로)
    var TARGET_READ_CHARACTERISTIC_UUID: UUID? = ACCEL_TEXT_CHAR_UUID

    // 쓰기 요청 대상 특성: cmdChar (BLEWrite 속성이 있으므로)
    var TARGET_WRITE_CHARACTERISTIC_UUID: UUID? = CMD_CHAR_UUID

    // 알림(Notify) 설정 대상 특성: accelTextChar (BLENotify 속성이 있으므로)
    var TARGET_NOTIFY_CHARACTERISTIC_UUID: UUID? = ACCEL_TEXT_CHAR_UUID


    fun init(context: Context) {
        if (applicationContext == null) {
            applicationContext = context.applicationContext
            val bluetoothManager = applicationContext?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = bluetoothManager?.adapter
            if (bluetoothAdapter == null) {
                Log.e(TAG, "Bluetooth not supported on this device.")
            } else {
                Log.d(TAG, "BleConnectionManager initialized.")
            }
        }
    }

    fun registerListener(listener: BleConnectionListener) {
        listeners.add(listener)
        Log.d(TAG, "Listener registered: $listener. Current listeners: ${listeners.size}")
    }

    fun unregisterListener(listener: BleConnectionListener) {
        listeners.remove(listener)
        Log.d(TAG, "Listener unregistered: $listener. Current listeners: ${listeners.size}")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val deviceAddress = gatt?.device?.address
            Log.d(TAG, "onConnectionStateChange: device $deviceAddress, status $status, newState $newState")

            connectionState = newState
            connectedDevice = if (newState == BluetoothProfile.STATE_CONNECTED) gatt?.device else null

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt = gatt // GATT 객체 저장
                Log.i(TAG, "Connected to GATT server. Attempting to start service discovery...")
                mainHandler.postDelayed({
                    bluetoothGatt?.discoverServices()
                }, 600) // 600ms 딜레이 (안정성 향상 목적)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.")
                closeGatt() // GATT 객체 정리
            }

            mainHandler.post {
                listeners.forEach { it.onConnectionStateChanged(newState, gatt) }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered successfully for device: ${gatt?.device?.address}")
                // TARGET_NOTIFY_CHARACTERISTIC_UUID (ACCEL_TEXT_CHAR_UUID)에 대해 알림 자동 활성화
                TARGET_NOTIFY_CHARACTERISTIC_UUID?.let { notifyCharUuid ->
                    enableNotifications(TARGET_SERVICE_UUID, notifyCharUuid)
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received error: $status for device: ${gatt?.device?.address}")
            }
            mainHandler.post {
                listeners.forEach { it.onServicesDiscovered(gatt, status) }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray, // API 33+
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            val characteristicUuid = characteristic.uuid
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Characteristic $characteristicUuid read successfully, value: ${value.toHexString()}")
            } else {
                Log.w(TAG, "Characteristic read failed for $characteristicUuid, status: $status")
            }
            mainHandler.post {
                listeners.forEach { it.onCharacteristicRead(gatt, characteristic, value, status) }
            }
        }
        // For devices below API 33, this callback is used for characteristic read.
        // If your minSdk is below 33, you might need to handle characteristic.value here.
        // override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        //    super.onCharacteristicRead(gatt, characteristic, status)
        //    // Handle characteristic.value for older APIs if necessary
        // }


        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            val characteristicUuid = characteristic?.uuid
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Characteristic $characteristicUuid written successfully.")
            } else {
                Log.w(TAG, "Characteristic write failed for $characteristicUuid, status: $status")
            }
            mainHandler.post {
                listeners.forEach { it.onCharacteristicWrite(gatt, characteristic, status) }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray // API 33+
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            val characteristicUuid = characteristic.uuid
            Log.i(TAG, "Characteristic $characteristicUuid changed, value: ${value.toHexString()}")
            mainHandler.post {
                listeners.forEach { it.onCharacteristicChanged(gatt, characteristic, value) }
            }
        }
        // For devices below API 33, this callback is used for characteristic changed.
        // If your minSdk is below 33, you might need to handle characteristic.value here.
        // override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        //    super.onCharacteristicChanged(gatt, characteristic)
        //    // Handle characteristic.value for older APIs if necessary
        // }


        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Descriptor ${descriptor?.uuid} written successfully.")
            } else {
                Log.w(TAG, "Descriptor write failed for ${descriptor?.uuid}, status: $status")
            }
            mainHandler.post {
                listeners.forEach { it.onDescriptorWrite(gatt, descriptor, status) }
            }
        }
    }

    fun connect(deviceAddress: String): Boolean {
        if (applicationContext == null) {
            Log.e(TAG, "BleConnectionManager not initialized. Call init() first.")
            return false
        }
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.w(TAG, "BluetoothAdapter not initialized or not enabled.")
            return false
        }

        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        if (device == null) {
            Log.w(TAG, "Device not found with address: $deviceAddress. Unable to connect.")
            return false
        }

        if (bluetoothGatt != null && deviceAddress == connectedDevice?.address &&
            (connectionState == BluetoothProfile.STATE_CONNECTING || connectionState == BluetoothProfile.STATE_CONNECTED)) {
            Log.d(TAG, "Already connected or connecting to $deviceAddress. Ignoring duplicate connect request.")
            mainHandler.post { listeners.forEach { it.onConnectionStateChanged(connectionState, bluetoothGatt) } }
            return true
        }

        if (bluetoothGatt != null && deviceAddress != connectedDevice?.address) {
            Log.d(TAG, "Connecting to a new device. Disconnecting from previous device: ${connectedDevice?.address}")
            disconnect()
        }

        Log.d(TAG, "Attempting to connect to device: ${try{device.name}catch(e: SecurityException){"Unknown"}} (${device.address})")
        connectionState = BluetoothProfile.STATE_CONNECTING
        mainHandler.post { listeners.forEach { it.onConnectionStateChanged(connectionState, null) } }

        bluetoothGatt = device.connectGatt(applicationContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        if (bluetoothGatt == null) {
            Log.e(TAG, "connectGatt returned null for device ${device.address}")
            connectionState = BluetoothProfile.STATE_DISCONNECTED
            mainHandler.post { listeners.forEach { it.onConnectionStateChanged(connectionState, null) } }
            return false
        }
        return true
    }

    fun readCharacteristic(serviceUuid: UUID, characteristicUuid: UUID) {
        if (bluetoothGatt == null || connectionState != BluetoothProfile.STATE_CONNECTED) {
            Log.w(TAG, "Not connected to any device. Cannot read characteristic.")
            return
        }
        val service = bluetoothGatt?.getService(serviceUuid)
        if (service == null) {
            Log.w(TAG, "Service $serviceUuid not found.")
            return
        }
        val characteristic = service.getCharacteristic(characteristicUuid)
        if (characteristic == null) {
            Log.w(TAG, "Characteristic $characteristicUuid not found in service $serviceUuid.")
            return
        }
        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
            Log.w(TAG, "Characteristic $characteristicUuid is not readable.")
            return
        }

        // For API 33+, readCharacteristic(characteristic, value, status) is called in callback.
        // For older APIs, readCharacteristic(characteristic, status) is called.
        // The read operation itself is initiated the same way.
        if (bluetoothGatt?.readCharacteristic(characteristic) == true) {
            Log.i(TAG, "Attempting to read characteristic: $characteristicUuid")
        } else {
            Log.w(TAG, "Failed to initiate read for characteristic: $characteristicUuid")
        }
    }

    fun readTargetCharacteristic() {
        TARGET_READ_CHARACTERISTIC_UUID?.let { readCharUuid ->
            Log.d(TAG, "Reading TARGET_READ_CHARACTERISTIC_UUID: $readCharUuid")
            readCharacteristic(TARGET_SERVICE_UUID, readCharUuid)
        } ?: TARGET_NOTIFY_CHARACTERISTIC_UUID?.let { notifyCharUuid ->
            Log.d(TAG, "TARGET_READ_CHARACTERISTIC_UUID is null, attempting to read TARGET_NOTIFY_CHARACTERISTIC_UUID ($notifyCharUuid) instead.")
            readCharacteristic(TARGET_SERVICE_UUID, notifyCharUuid) // 알림 특성도 읽기 속성이 있다면 읽을 수 있음
        } ?: Log.w(TAG, "No target characteristic UUID for reading is defined.")
    }


    fun writeCharacteristic(serviceUuid: UUID, characteristicUuid: UUID, data: ByteArray, writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
        if (bluetoothGatt == null || connectionState != BluetoothProfile.STATE_CONNECTED) {
            Log.w(TAG, "Not connected. Cannot write characteristic.")
            return
        }
        val service = bluetoothGatt?.getService(serviceUuid)
        if (service == null) {
            Log.w(TAG, "Service $serviceUuid not found for writing.")
            return
        }
        val characteristic = service.getCharacteristic(characteristicUuid)
        if (characteristic == null) {
            Log.w(TAG, "Characteristic $characteristicUuid not found for writing.")
            return
        }

        val writeProperty = if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.PROPERTY_WRITE
        }

        if ((characteristic.properties and writeProperty) == 0) {
            Log.w(TAG, "Characteristic $characteristicUuid does not support the specified write type ($writeType). Properties: ${characteristic.properties}")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 (API 33) and above use the new writeCharacteristic method
            val result = bluetoothGatt?.writeCharacteristic(characteristic, data, writeType)
            Log.d(TAG, "writeCharacteristic (API 33+) called for $characteristicUuid, result: $result, writeType: $writeType")
        } else {
            // For older versions
            characteristic.value = data
            characteristic.writeType = writeType
            val result = bluetoothGatt?.writeCharacteristic(characteristic)
            Log.d(TAG, "writeCharacteristic (legacy) called for $characteristicUuid, result: $result, writeType: $writeType")
        }
    }

    fun enableNotifications(serviceUuid: UUID, characteristicUuid: UUID) {
        if (bluetoothGatt == null || connectionState != BluetoothProfile.STATE_CONNECTED) {
            Log.w(TAG, "Not connected. Cannot enable notifications.")
            return
        }
        val service = bluetoothGatt?.getService(serviceUuid)
        if (service == null) {
            Log.w(TAG, "Service $serviceUuid not found for notifications.")
            return
        }
        val characteristic = service.getCharacteristic(characteristicUuid)
        if (characteristic == null) {
            Log.w(TAG, "Characteristic $characteristicUuid not found for notifications.")
            return
        }
        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
            Log.w(TAG, "Characteristic $characteristicUuid does not support notifications. Properties: ${characteristic.properties}")
            return
        }

        if (bluetoothGatt?.setCharacteristicNotification(characteristic, true) == true) {
            val descriptor = characteristic.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                val enableValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val result = bluetoothGatt?.writeDescriptor(descriptor, enableValue)
                    Log.d(TAG, "writeDescriptor (API 33+) for enabling notifications on $characteristicUuid, result: $result")
                } else {
                    descriptor.value = enableValue
                    val result = bluetoothGatt?.writeDescriptor(descriptor)
                    Log.d(TAG, "writeDescriptor (legacy) for enabling notifications on $characteristicUuid, result: $result")
                }
            } else {
                Log.w(TAG, "CCCD descriptor ($CCCD_UUID) not found for characteristic $characteristicUuid.")
            }
        } else {
            Log.w(TAG, "Failed to set characteristic notification for $characteristicUuid.")
        }
    }

    fun disableNotifications(serviceUuid: UUID, characteristicUuid: UUID) {
        if (bluetoothGatt == null || connectionState != BluetoothProfile.STATE_CONNECTED) {
            Log.w(TAG, "Not connected. Cannot disable notifications.")
            return
        }
        val service = bluetoothGatt?.getService(serviceUuid)
        if (service == null) {
            Log.w(TAG, "Service $serviceUuid not found for disabling notifications.")
            return
        }
        val characteristic = service.getCharacteristic(characteristicUuid)
        if (characteristic == null) {
            Log.w(TAG, "Characteristic $characteristicUuid not found for disabling notifications.")
            return
        }
        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0 &&
            (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) == 0) { // Indicate도 확인
            Log.w(TAG, "Characteristic $characteristicUuid does not support notifications or indications.")
            return
        }

        if (bluetoothGatt?.setCharacteristicNotification(characteristic, false) == true) {
            val descriptor = characteristic.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                val disableValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val result = bluetoothGatt?.writeDescriptor(descriptor, disableValue)
                    Log.d(TAG, "writeDescriptor (API 33+) for disabling notifications on $characteristicUuid, result: $result")
                } else {
                    descriptor.value = disableValue
                    val result = bluetoothGatt?.writeDescriptor(descriptor)
                    Log.d(TAG, "writeDescriptor (legacy) for disabling notifications on $characteristicUuid, result: $result")
                }
            } else {
                Log.w(TAG, "CCCD descriptor ($CCCD_UUID) not found for characteristic $characteristicUuid when disabling.")
            }
        } else {
            Log.w(TAG, "Failed to set characteristic notification to false for $characteristicUuid.")
        }
    }

    fun disconnect() {
        if (bluetoothGatt == null) {
            Log.d(TAG, "disconnect() called but no GATT connection to close.")
            if (connectionState != BluetoothProfile.STATE_DISCONNECTED) {
                connectionState = BluetoothProfile.STATE_DISCONNECTED
                mainHandler.post { listeners.forEach { it.onConnectionStateChanged(connectionState, null) } }
            }
            return
        }
        Log.d(TAG, "Disconnecting GATT connection to ${bluetoothGatt?.device?.address}")
        bluetoothGatt?.disconnect()
    }

    private fun closeGatt() {
        if (bluetoothGatt != null) {
            Log.d(TAG, "Closing GATT client for ${bluetoothGatt?.device?.address}")
            bluetoothGatt?.close()
            bluetoothGatt = null
        }
        Log.i(TAG, "GATT client closed.")
    }

    fun isConnected(): Boolean {
        return connectionState == BluetoothProfile.STATE_CONNECTED
    }
}

fun ByteArray.toHexString(): String = joinToString(separator = " ") { String.format("%02X", it) }

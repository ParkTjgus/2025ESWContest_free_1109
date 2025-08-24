package com.example.app.ble // 실제 패키지명으로 변경하세요

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattDescriptor // 추가된 import
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.app.R // 실제 R 클래스 경로로 변경하세요
// BleConnectionManager는 com.example.app.ble 패키지에 있다고 가정
// BleScanActivity도 com.example.app.ble 패키지에 있다고 가정

class DeviceControlActivity : AppCompatActivity(), BleConnectionManager.BleConnectionListener {

    private lateinit var tvDeviceName: TextView
    private lateinit var tvDeviceAddress: TextView
    private lateinit var tvConnectionState: TextView
    private lateinit var tvReceivedData: TextView
    private lateinit var btnReadData: Button

    private var deviceName: String? = null
    private var deviceAddress: String? = null

    companion object {
        private const val TAG = "DeviceControlActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_control)

        deviceName = intent.getStringExtra(BleScanActivity.EXTRA_DEVICE_NAME)
        deviceAddress = intent.getStringExtra(BleScanActivity.EXTRA_DEVICE_ADDRESS)

        if (deviceAddress == null) {
            Log.e(TAG, "Device address is null. Finishing activity.")
            Toast.makeText(this, "기기 주소를 찾을 수 없습니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        tvDeviceName = findViewById(R.id.tvDeviceName)
        tvDeviceAddress = findViewById(R.id.tvDeviceAddress)
        tvConnectionState = findViewById(R.id.tvConnectionState)
        tvReceivedData = findViewById(R.id.tvReceivedData)
        btnReadData = findViewById(R.id.btnReadData)

        tvDeviceName.text = "Device Name: ${deviceName ?: "Unknown"}"
        tvDeviceAddress.text = "Address: $deviceAddress"
        tvConnectionState.text = "Status: Initializing..." // 초기 상태

        BleConnectionManager.registerListener(this)

        if (BleConnectionManager.connectedDevice?.address == deviceAddress && BleConnectionManager.isConnected()) {
            Log.d(TAG, "Already connected to $deviceAddress via BleConnectionManager.")
            updateConnectionState(BluetoothProfile.STATE_CONNECTED)
        } else if (BleConnectionManager.connectionState == BluetoothProfile.STATE_CONNECTING && BleConnectionManager.connectedDevice?.address == deviceAddress) {
            Log.d(TAG, "Currently connecting to $deviceAddress via BleConnectionManager.")
            updateConnectionState(BluetoothProfile.STATE_CONNECTING)
        }
        else {
            Log.w(TAG, "Not connected to $deviceAddress. Current state: ${BleConnectionManager.connectionState}, Manager connected device: ${BleConnectionManager.connectedDevice?.address}")
            updateConnectionState(BluetoothProfile.STATE_DISCONNECTED) // 명시적으로 연결 안된 상태로 UI 업데이트
            // 만약 자동으로 재연결을 시도하고 싶다면 아래 주석 해제 (단, BleConnectionManager에서 connect 호출 시 이전 연결 해제 로직 주의)
            // Log.d(TAG, "Attempting to connect to $deviceAddress now.")
            // BleConnectionManager.connect(deviceAddress)
        }

        btnReadData.setOnClickListener {
            if (BleConnectionManager.isConnected() && BleConnectionManager.connectedDevice?.address == deviceAddress) {
                Log.d(TAG, "Read Data button clicked. Requesting characteristic read.")
                // BleConnectionManager.readTargetCharacteristic() 함수는 내부적으로 TARGET_READ_CHARACTERISTIC_UUID 또는 TARGET_NOTIFY_CHARACTERISTIC_UUID 를 읽음
                BleConnectionManager.readTargetCharacteristic()
            } else {
                Toast.makeText(this, "기기에 연결되어 있지 않거나 대상 기기가 아닙니다.", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Read Data button: Not connected or wrong device. Manager connected: ${BleConnectionManager.isConnected()}, Target: $deviceAddress, Manager state: ${BleConnectionManager.connectionState}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        BleConnectionManager.unregisterListener(this)
        Log.d(TAG, "onDestroy called. Unregistered from BleConnectionManager.")
    }

    private fun updateConnectionState(newState: Int) {
        runOnUiThread {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    tvConnectionState.text = "Status: Connected"
                    btnReadData.isEnabled = true
                    Log.i(TAG, "UI Updated: Connected to $deviceName ($deviceAddress)")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    tvConnectionState.text = "Status: Disconnected"
                    btnReadData.isEnabled = false
                    tvReceivedData.text = "연결 끊김" // 또는 이전 데이터 유지
                    Log.i(TAG, "UI Updated: Disconnected from $deviceName ($deviceAddress)")
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    tvConnectionState.text = "Status: Connecting..."
                    btnReadData.isEnabled = false
                    Log.i(TAG, "UI Updated: Connecting to $deviceName ($deviceAddress)")
                }
            }
        }
    }

    // --- BleConnectionManager.BleConnectionListener 구현 ---
    override fun onConnectionStateChanged(newState: Int, gatt: BluetoothGatt?) {
        val eventDeviceAddress = gatt?.device?.address
        // 이 Activity가 관심 있는 기기(deviceAddress)에 대한 연결 변경이거나,
        // 이 Activity가 특정 기기를 지정하지 않았는데(deviceAddress == null) 연결이 끊긴 경우(모든 연결 끊김에 반응)
        // 또는 이벤트의 기기 주소가 현재 이 Activity의 대상 기기와 같을 때만 UI 업데이트
        if (eventDeviceAddress == deviceAddress || (deviceAddress == null && newState == BluetoothProfile.STATE_DISCONNECTED)) {
            Log.d(TAG, "onConnectionStateChanged: Relevant state change. newState=$newState for device $eventDeviceAddress (Target: $deviceAddress)")
            updateConnectionState(newState)
        } else {
            Log.d(TAG, "onConnectionStateChanged: Unrelated state change. newState=$newState for device $eventDeviceAddress (Target: $deviceAddress). Ignoring.")
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        if (gatt?.device?.address == deviceAddress) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered successfully for $deviceName.")
                runOnUiThread { Toast.makeText(this, "서비스 발견 완료 ($deviceName)", Toast.LENGTH_SHORT).show() }
                // 서비스 발견 후 자동으로 특정 특성을 읽거나 알림을 설정하고 싶다면 여기서 BleConnectionManager의 함수 호출
                // 예: BleConnectionManager.enableNotifications(BleConnectionManager.TARGET_SERVICE_UUID, BleConnectionManager.TARGET_NOTIFY_CHARACTERISTIC_UUID)
                // 예: BleConnectionManager.readTargetCharacteristic()
            } else {
                Log.w(TAG, "Service discovery failed for $deviceName with status: $status")
                runOnUiThread { Toast.makeText(this, "서비스 발견 실패: $status", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, value: ByteArray?, status: Int) {
        // 읽기 요청한 특성(TARGET_READ_CHARACTERISTIC_UUID) 또는 btnReadData에서 읽기를 시도한 알림 특성(TARGET_NOTIFY_CHARACTERISTIC_UUID)에 대한 응답인지 확인
        val targetReadUuid = BleConnectionManager.TARGET_READ_CHARACTERISTIC_UUID
        val targetNotifyUuidForRead = BleConnectionManager.TARGET_NOTIFY_CHARACTERISTIC_UUID // readTargetCharacteristic()에서 읽을 수 있음

        if (gatt?.device?.address == deviceAddress &&
            (characteristic?.uuid == targetReadUuid || characteristic?.uuid == targetNotifyUuidForRead) ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val dataString = value?.let { String(it, Charsets.UTF_8) } ?: "N/A"
                val hexString = value?.joinToString(separator = " ") { String.format("%02X", it) } ?: "N/A"
                Log.i(TAG, "Characteristic ${characteristic?.uuid} read successfully. Value (UTF-8): '$dataString', (Hex): $hexString")
                runOnUiThread {
                    tvReceivedData.text = "읽은 데이터 (UTF-8): $dataString\n읽은 데이터 (Hex): $hexString"
                    Toast.makeText(this, "데이터 읽기 성공: $dataString", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w(TAG, "Characteristic read failed for ${characteristic?.uuid}, status: $status")
                runOnUiThread {
                    tvReceivedData.text = "데이터 읽기 실패 (Status: $status)"
                    Toast.makeText(this, "데이터 읽기 실패", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Log.d(TAG, "onCharacteristicRead for ${characteristic?.uuid} (device: ${gatt?.device?.address}), but not the target or wrong device.")
        }
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
        if (gatt?.device?.address == deviceAddress) { // 현재 Activity가 관리하는 기기에 대한 이벤트인지 확인
            val charUuid = characteristic?.uuid ?: "Unknown Characteristic"
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Characteristic $charUuid written successfully.")
                runOnUiThread { Toast.makeText(this, "$charUuid 쓰기 성공", Toast.LENGTH_SHORT).show() }
            } else {
                Log.w(TAG, "Characteristic write failed for $charUuid with status: $status")
                runOnUiThread { Toast.makeText(this, "$charUuid 쓰기 실패: $status", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, value: ByteArray?) {
        // TARGET_NOTIFY_CHARACTERISTIC_UUID와 일치하는지 확인
        if (gatt?.device?.address == deviceAddress && characteristic?.uuid == BleConnectionManager.TARGET_NOTIFY_CHARACTERISTIC_UUID) {
            val dataString = value?.let { String(it, Charsets.UTF_8) } ?: "N/A"
            val hexString = value?.joinToString(separator = " ") { String.format("%02X", it) } ?: "N/A"
            Log.i(TAG, "Characteristic ${characteristic.uuid} changed (Notification). Value (UTF-8): '$dataString', (Hex): $hexString")
            runOnUiThread {
                tvReceivedData.text = "알림 수신 (UTF-8): $dataString\n알림 수신 (Hex): $hexString"
                Toast.makeText(this, "알림 데이터: $dataString", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d(TAG, "Characteristic changed for ${characteristic?.uuid} (device: ${gatt?.device?.address}), but not the target notify characteristic or wrong device.")
        }
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
        if (gatt?.device?.address == deviceAddress) { // 현재 Activity가 관리하는 기기에 대한 이벤트인지 확인
            val descUuid = descriptor?.uuid ?: "Unknown Descriptor"
            val charUuid = descriptor?.characteristic?.uuid ?: "Unknown Characteristic"
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Descriptor $descUuid for characteristic $charUuid written successfully (e.g., notification enabled).")
                runOnUiThread { Toast.makeText(this, "알림/Indication 설정 완료", Toast.LENGTH_SHORT).show() }
            } else {
                Log.w(TAG, "Descriptor write failed for $descUuid (char: $charUuid) with status: $status")
                runOnUiThread { Toast.makeText(this, "알림/Indication 설정 실패: $status", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}

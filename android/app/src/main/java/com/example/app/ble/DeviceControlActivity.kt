package com.example.app.ble // 실제 패키지명으로 변경하세요

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattDescriptor
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

    // --- 현재 운동 목표 속도 변수 추가 ---
    private var currentTargetSpeed: Int = 15 // 예시: 현재 운동 목표 속도 (필요에 따라 값 변경 또는 다른 곳에서 설정)
    // ---

    companion object {
        private const val TAG = "DeviceControlActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_control) // activity_device_control 레이아웃 사용 가정

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
        btnReadData = findViewById(R.id.btnReadData) // activity_device_control 에 이 ID가 있다고 가정

        tvDeviceName.text = "Device Name: ${deviceName ?: "Unknown"}"
        tvDeviceAddress.text = "Address: $deviceAddress"
        tvConnectionState.text = "Status: Initializing..."

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
            updateConnectionState(BluetoothProfile.STATE_DISCONNECTED)
            // 자동으로 재연결을 시도하지 않는 것으로 가정
        }

        btnReadData.setOnClickListener {
            if (BleConnectionManager.isConnected() && BleConnectionManager.connectedDevice?.address == deviceAddress) {
                Log.d(TAG, "Read Data button clicked. Requesting characteristic read.")
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
        // 연결 해제 로직은 BleScanActivity 또는 앱 전체 생명주기 관리에서 처리될 수 있음
        // 만약 이 Activity가 닫힐 때 항상 연결을 끊어야 한다면 다음 주석 해제
        // if (BleConnectionManager.connectedDevice?.address == deviceAddress) {
        //     BleConnectionManager.disconnect()
        // }
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
                    tvReceivedData.text = "연결 끊김"
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

    override fun onConnectionStateChanged(newState: Int, gatt: BluetoothGatt?) {
        val eventDeviceAddress = gatt?.device?.address
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
                // BleConnectionManager에서 TARGET_NOTIFY_CHARACTERISTIC_UUID에 대해 알림 자동 활성화 시도
            } else {
                Log.w(TAG, "Service discovery failed for $deviceName with status: $status")
                runOnUiThread { Toast.makeText(this, "서비스 발견 실패: $status", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, value: ByteArray?, status: Int) {
        val targetReadUuid = BleConnectionManager.TARGET_READ_CHARACTERISTIC_UUID
        val targetNotifyUuidForRead = BleConnectionManager.TARGET_NOTIFY_CHARACTERISTIC_UUID

        if (gatt?.device?.address == deviceAddress &&
            (characteristic?.uuid == targetReadUuid || characteristic?.uuid == targetNotifyUuidForRead) ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val dataString = value?.let { String(it, Charsets.UTF_8) } ?: "N/A"
                val hexString = value?.joinToString(separator = " ") { String.format("%02X", it) } ?: "N/A"
                Log.i(TAG, "Characteristic ${characteristic?.uuid} read successfully. Value (UTF-8): '$dataString', (Hex): $hexString")
                runOnUiThread {
                    tvReceivedData.text = "읽은 데이터 (UTF-8): $dataString\n읽은 데이터 (Hex): $hexString" // 기존 표시 방식 유지
                    Toast.makeText(this, "읽은 데이터: $dataString", Toast.LENGTH_SHORT).show()
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
        if (gatt?.device?.address == deviceAddress) {
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
        // TARGET_NOTIFY_CHARACTERISTIC_UUID (ACCEL_TEXT_CHAR_UUID)와 일치하는지 확인
        if (gatt?.device?.address == deviceAddress && characteristic?.uuid == BleConnectionManager.TARGET_NOTIFY_CHARACTERISTIC_UUID) {
            val dataString = value?.let { String(it, Charsets.UTF_8) } ?: "N/A"
            val hexString = value?.joinToString(separator = " ") { String.format("%02X", it) } ?: "N/A" // Hex는 로그용으로 계속 사용 가능
            Log.i(TAG, "Characteristic ${characteristic?.uuid} changed (Notification). Value (UTF-8): '$dataString', (Hex): $hexString")

            runOnUiThread {
                // 기존 알림 데이터 표시 로직 (Hex 표시 여부는 이전 논의에 따라 유지 또는 제거)
                tvReceivedData.text = "알림 수신 (UTF-8): $dataString\n알림 수신 (Hex): $hexString" // 기존 표시 방식 유지
                Toast.makeText(this, "알림 데이터: $dataString", Toast.LENGTH_SHORT).show()

                // --- "START_REQ" 수신 시 응답 로직 추가 ---
                if (dataString == "START_REQ") {
                    Log.d(TAG, "Received 'START_REQ'. Sending 'ACK_START' with target speed: $currentTargetSpeed.")

                    // 응답 데이터 생성: ["ACK_START", 현재운동목표속도]
                    val responseString = """[ACK_START,$currentTargetSpeed]"""
                    val responseBytes = responseString.toByteArray(Charsets.UTF_8)

                    BleConnectionManager.TARGET_WRITE_CHARACTERISTIC_UUID?.let { writeUuid ->
                        BleConnectionManager.writeCharacteristic(
                            BleConnectionManager.TARGET_SERVICE_UUID, // SENSOR_SERVICE_UUID
                            writeUuid, // CMD_CHAR_UUID
                            responseBytes,
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT // 필요시 WRITE_TYPE_NO_RESPONSE
                        )
                        Log.i(TAG, "Sent response to CMD_CHAR: $responseString")
                        Toast.makeText(this, "응답 전송: $responseString", Toast.LENGTH_SHORT).show()
                    } ?: Log.w(TAG, "TARGET_WRITE_CHARACTERISTIC_UUID is null. Cannot send ACK_START.")
                }
                // --- 응답 로직 끝 ---
            }
        } else {
            Log.d(TAG, "Characteristic changed for ${characteristic?.uuid} (device: ${gatt?.device?.address}), but not the target notify characteristic or wrong device.")
        }
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
        if (gatt?.device?.address == deviceAddress) {
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

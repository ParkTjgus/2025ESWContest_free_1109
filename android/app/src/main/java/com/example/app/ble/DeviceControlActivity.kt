package com.example.app.ble // 실제 패키지명으로 변경하세요

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattDescriptor
import android.content.Intent
import android.os.Bundle
import android.util.Log
// import android.widget.Button // Button import 제거
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.app.R // 실제 R 클래스 경로로 변경하세요
import com.example.app.exercise.ExerciseManager
import com.example.app.exercise.SessionState // ⭐ 올바른 SessionState import
import com.example.app.exercise.ExerciseSetActivity

class DeviceControlActivity : AppCompatActivity(), BleConnectionManager.BleConnectionListener {

    private lateinit var tvDeviceName: TextView
    private lateinit var tvDeviceAddress: TextView
    private lateinit var tvConnectionState: TextView
    // private lateinit var tvReceivedData: TextView // 제거됨
    // private lateinit var btnReadData: Button // 제거됨
    private lateinit var infoTextView: TextView // info TextView 참조 추가

    private var deviceName: String? = null
    private var deviceAddress: String? = null
    // private var currentTargetSpeed: Int = 15 // 현재 ACK_START 응답에 사용되지 않음

    private var pendingScreenTransitionToExerciseSet: Boolean = false // 화면 전환 대기 플래그

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
        infoTextView = findViewById(R.id.info) // info TextView 초기화

        tvDeviceName.text = "Device Name: ${deviceName ?: "Unknown"}"
        tvDeviceAddress.text = "Address: $deviceAddress"
        tvConnectionState.text = "Status: Initializing..."
        infoTextView.text = "블루투스 기기 초기화 중..." // 초기 메시지 설정

        BleConnectionManager.registerListener(this)

        if (BleConnectionManager.connectedDevice?.address == deviceAddress && BleConnectionManager.isConnected()) {
            updateConnectionState(BluetoothProfile.STATE_CONNECTED)
        } else if (BleConnectionManager.connectionState == BluetoothProfile.STATE_CONNECTING && BleConnectionManager.connectedDevice?.address == deviceAddress) {
            updateConnectionState(BluetoothProfile.STATE_CONNECTING)
        } else {
            updateConnectionState(BluetoothProfile.STATE_DISCONNECTED)
            if (deviceAddress != null) {
                Log.d(TAG, "Device address found. BLE connection will be managed by BleScanActivity or user interaction.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        BleConnectionManager.unregisterListener(this)
    }

    private fun updateConnectionState(newState: Int) {
        runOnUiThread {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    tvConnectionState.text = "Status: Connected"
                    infoTextView.text = "디바이스의 시작 신호 대기 중..."
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    tvConnectionState.text = "Status: Disconnected"
                    if (ExerciseManager.state == SessionState.FINISHED) {
                        infoTextView.text = "모든 운동 완료! (연결 해제됨)"
                    } else {
                        infoTextView.text = "연결 끊김. 다시 시도해주세요."
                    }
                    pendingScreenTransitionToExerciseSet = false // 연결 끊김 시 화면 전환 시도 중단
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    tvConnectionState.text = "Status: Connecting..."
                    infoTextView.text = "디바이스에 연결 중..."
                }
            }
        }
    }

    override fun onConnectionStateChanged(newState: Int, gatt: BluetoothGatt?) {
        if (gatt?.device?.address == deviceAddress) {
            updateConnectionState(newState)
        } else if (deviceAddress != null && newState == BluetoothProfile.STATE_DISCONNECTED && gatt?.device?.address == BleConnectionManager.connectedDevice?.address) {
            // This case might be if another part of the app disconnects the device.
            // For this activity, we are primarily concerned with `deviceAddress`.
        } else if (deviceAddress == null && newState == BluetoothProfile.STATE_DISCONNECTED) {
            // This case should ideally not happen if deviceAddress is checked in onCreate.
            updateConnectionState(newState)
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        if (gatt?.device?.address == deviceAddress) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread {
                    Toast.makeText(this, "서비스 발견 완료 ($deviceName)", Toast.LENGTH_SHORT).show()
                    infoTextView.text = "서비스 발견! 디바이스 시작 신호 대기 중..."
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "서비스 발견 실패: $status", Toast.LENGTH_SHORT).show()
                    infoTextView.text = "서비스 발견 실패. 연결 확인 필요."
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, value: ByteArray?, status: Int) {
        if (gatt?.device?.address == deviceAddress &&
            (characteristic?.uuid == BleConnectionManager.TARGET_READ_CHARACTERISTIC_UUID || characteristic?.uuid == BleConnectionManager.TARGET_NOTIFY_CHARACTERISTIC_UUID) ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val dataString = value?.let { String(it, Charsets.UTF_8) } ?: "N/A"
                runOnUiThread {
                    Toast.makeText(this, "읽은 데이터: $dataString", Toast.LENGTH_SHORT).show()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "데이터 읽기 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
        if (gatt?.device?.address == deviceAddress) {
            val charUuid = characteristic?.uuid ?: "Unknown Characteristic"
            runOnUiThread { // 모든 UI 업데이트는 runOnUiThread에서
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Toast.makeText(this, "$charUuid 쓰기 성공", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Characteristic $charUuid write successful.")

                    if (charUuid == BleConnectionManager.TARGET_WRITE_CHARACTERISTIC_UUID && pendingScreenTransitionToExerciseSet) {
                        Log.d(TAG, "ACK_START write confirmed, proceeding to ExerciseSetActivity. ExerciseManager state: ${ExerciseManager.state}")

                        val exerciseSetIntent = Intent(this@DeviceControlActivity, ExerciseSetActivity::class.java)
                        startActivity(exerciseSetIntent)
                        infoTextView.text = "운동 화면으로 이동합니다..."

                        pendingScreenTransitionToExerciseSet = false // 플래그 리셋
                    }
                } else {
                    Toast.makeText(this, "$charUuid 쓰기 실패: $status", Toast.LENGTH_SHORT).show()
                    Log.w(TAG, "Characteristic $charUuid write failed, status: $status")
                    // 쓰기 실패 시 전환 시도 중단 및 사용자에게 알림
                    if (charUuid == BleConnectionManager.TARGET_WRITE_CHARACTERISTIC_UUID && pendingScreenTransitionToExerciseSet) {
                        infoTextView.text = "'ACK_START' 전송 실패. 재시도 필요."
                    }
                    pendingScreenTransitionToExerciseSet = false
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, value: ByteArray?) {
        if (gatt?.device?.address == deviceAddress && characteristic?.uuid == BleConnectionManager.TARGET_NOTIFY_CHARACTERISTIC_UUID) {
            val dataString = value?.let { String(it, Charsets.UTF_8) } ?: "N/A"
            Log.i(TAG, "Notification Received: (UTF-8): '$dataString'")

            runOnUiThread {
                if (dataString == "START_REQ") {
                    infoTextView.text = "'START_REQ' 수신! 응답 전송 및 운동 준비..."
                    Log.d(TAG, "Received 'START_REQ'. Current ExerciseManager state: ${ExerciseManager.state}")

                    val responseToDevice = "ACK_START" // currentTargetSpeed는 현재 응답에 포함되지 않음
                    val responseBytes = responseToDevice.toByteArray(Charsets.UTF_8)

                    pendingScreenTransitionToExerciseSet = false // 새로운 START_REQ 처리 전 플래그 리셋

                    BleConnectionManager.TARGET_WRITE_CHARACTERISTIC_UUID?.let { writeUuid ->
                        BleConnectionManager.writeCharacteristic(
                            BleConnectionManager.TARGET_SERVICE_UUID,
                            writeUuid,
                            responseBytes,
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        )
                        Log.i(TAG, "Attempting to send 'ACK_START' response to device: $responseToDevice")
                        // Toast는 onCharacteristicWrite 성공/실패 시 보여주는 것이 더 정확함
                    } ?: Log.w(TAG, "TARGET_WRITE_CHARACTERISTIC_UUID is null. Cannot send ACK_START.")

                    var shouldProceedToExerciseSet = false
                    when (ExerciseManager.state) {
                        SessionState.IDLE -> {
                            Log.d(TAG, "ExerciseManager is IDLE. Preparing next exercise.")
                            if (ExerciseManager.prepareAndStartNextExercise()) {
                                shouldProceedToExerciseSet = true
                            } else {
                                Log.i(TAG, "All exercises finished according to ExerciseManager.")
                                Toast.makeText(this, "모든 운동이 완료되었습니다!", Toast.LENGTH_LONG).show()
                                infoTextView.text = "모든 운동 완료! 연결 해제합니다."
                                if (BleConnectionManager.isConnected()) {
                                    Log.i(TAG, "Disconnecting BLE as all exercises are finished.")
                                    BleConnectionManager.disconnect()
                                }
                            }
                        }
                        SessionState.WORKING -> {
                            Log.d(TAG, "ExerciseManager is already WORKING. Proceeding with current exercise: ${ExerciseManager.getCurrentExercise()?.name}")
                            shouldProceedToExerciseSet = true
                        }
                        SessionState.RESTING -> {
                            Log.w(TAG, "Received START_REQ while ExerciseManager is RESTING. Forcing to working.")
                            ExerciseManager.finishRest()
                            if (ExerciseManager.state == SessionState.WORKING) {
                                shouldProceedToExerciseSet = true
                            } else {
                                Log.e(TAG, "Failed to transition from RESTING to WORKING for START_REQ.")
                                infoTextView.text = "오류: 휴식 후 운동 시작 실패"
                            }
                        }
                        SessionState.FINISHED -> {
                            Log.i(TAG, "Received START_REQ but ExerciseManager is already FINISHED.")
                            Toast.makeText(this, "모든 운동이 이미 완료되었습니다.", Toast.LENGTH_LONG).show()
                            infoTextView.text = "모든 운동 이미 완료됨. 연결 해제합니다."
                            if (BleConnectionManager.isConnected()) {
                                Log.i(TAG, "Disconnecting BLE as session is already finished.")
                                BleConnectionManager.disconnect()
                            }
                        }
                    }

                    if (shouldProceedToExerciseSet) {
                        pendingScreenTransitionToExerciseSet = true
                        Log.d(TAG, "ACK_START send attempt initiated. Will proceed to ExerciseSetActivity after write confirmation if successful.")
                        // infoTextView.text = "응답 전송 완료. 운동 화면으로 이동 대기 중..."; // 사용자에게 상태 알림
                    }
                } else {
                    // "START_REQ"가 아닌 다른 데이터 수신 시 처리
                    // infoTextView.text = "알림 수신: $dataString" // 예시
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: DeviceControlActivity resumed. ExerciseManager state: ${ExerciseManager.state}")
        pendingScreenTransitionToExerciseSet = false // 화면이 다시 활성화될 때 플래그 초기화

        if (BleConnectionManager.isConnected() && BleConnectionManager.connectedDevice?.address == deviceAddress) {
            updateConnectionState(BluetoothProfile.STATE_CONNECTED)

            if (ExerciseManager.state == SessionState.IDLE && ExerciseManager.getCurrentExercise() != null) {
                infoTextView.text = "디바이스의 시작 신호 대기 중 ..."
            } else if (ExerciseManager.state == SessionState.FINISHED) {
                infoTextView.text = "모든 운동 완료!"
                Log.i(TAG, "onResume: All exercises finished. Disconnecting BLE if connected.")
                if (BleConnectionManager.isConnected()) {
                    BleConnectionManager.disconnect()
                }
            }
        } else {
            updateConnectionState(BluetoothProfile.STATE_DISCONNECTED)
            if (ExerciseManager.state == SessionState.FINISHED) {
                infoTextView.text = "모든 운동 완료! (연결 해제됨)"
                Log.i(TAG, "onResume: All exercises finished and already disconnected or not connected to target.")
            }
        }
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
        if (gatt?.device?.address == deviceAddress) {
            runOnUiThread {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Toast.makeText(this, "알림/Indication 설정 완료", Toast.LENGTH_SHORT).show()
                    // infoTextView.text = "알림 설정 완료. 디바이스 시작 신호 대기 중...";
                } else {
                    Toast.makeText(this, "알림/Indication 설정 실패: $status", Toast.LENGTH_SHORT).show()
                    // infoTextView.text = "알림 설정 실패 ($status)";
                }
            }
        }
    }
}

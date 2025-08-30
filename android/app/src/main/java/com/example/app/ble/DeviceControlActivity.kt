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
import com.example.app.exercise.ExerciseItem // ExerciseManager.getCurrentExercise()의 반환 타입이므로 import 필요
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

                    // ACK_START 쓰기 성공 후 화면 전환 로직은 여기에서 처리
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
                    Log.d(TAG, "Received 'START_REQ'. Initial ExerciseManager state: ${ExerciseManager.state}")

                    pendingScreenTransitionToExerciseSet = false // 새로운 START_REQ 처리 전 플래그 리셋

                    var shouldProceedToExerciseSet = false
                    var exerciseThatWillStart: ExerciseItem? = null // 시작할 운동 정보를 담을 변수
                    var currentRoundTripTime = 0 // 현재 운동의 목표 시간을 저장할 변수 (기본값 0)

                    // 운동 세션 완료 처리를 위한 공통 함수
                    fun handleAllExercisesFinished() {
                        Log.i(TAG, "All exercises finished according to ExerciseManager.")
                        Toast.makeText(this, "모든 운동이 완료되었습니다!", Toast.LENGTH_LONG).show()
                        infoTextView.text = "모든 운동 완료! 연결 해제합니다."
                        if (BleConnectionManager.isConnected()) {
                            Log.i(TAG, "Disconnecting BLE as all exercises are finished.")
                            BleConnectionManager.disconnect()
                        }
                        shouldProceedToExerciseSet = false // 화면 전환 안 함
                    }

                    // 1. ExerciseManager 상태에 따라 다음 운동 준비 및 목표 시간 가져오기
                    when (ExerciseManager.state) {
                        SessionState.IDLE -> {
                            Log.d(TAG, "ExerciseManager is IDLE. Preparing next exercise.")
                            if (ExerciseManager.prepareAndStartNextExercise()) { // 다음 운동 준비 및 시작
                                shouldProceedToExerciseSet = true
                                exerciseThatWillStart = ExerciseManager.getCurrentExercise() // 방금 시작된 운동 정보
                                currentRoundTripTime = exerciseThatWillStart?.roundTripTime ?: 0
                                Log.d(TAG, "IDLE -> Next exercise: ${exerciseThatWillStart?.name}, RoundTripTime: $currentRoundTripTime")
                            } else {
                                handleAllExercisesFinished()
                            }
                        }
                        SessionState.WORKING -> {
                            // WORKING 상태에서는 다음 운동으로 넘어가지 않고, 현재 운동 정보를 사용 (스킵 방지)
                            Log.d(TAG, "ExerciseManager is WORKING. START_REQ received. Using current exercise.")
                            exerciseThatWillStart = ExerciseManager.getCurrentExercise()
                            if (exerciseThatWillStart != null) {
                                shouldProceedToExerciseSet = true // 현재 운동에 대한 확인/재시작으로 간주
                                currentRoundTripTime = exerciseThatWillStart.roundTripTime
                                Log.d(TAG, "WORKING -> Current exercise: ${exerciseThatWillStart.name}, RoundTripTime: $currentRoundTripTime")
                            } else {
                                Log.e(TAG, "ExerciseManager is WORKING but getCurrentExercise() is null. Treating as finished.")
                                handleAllExercisesFinished()
                            }
                        }
                        SessionState.RESTING -> {
                            Log.w(TAG, "Received START_REQ while ExerciseManager is RESTING. Finishing rest and preparing next exercise.")
                            ExerciseManager.finishRest() // 휴식 종료
                            if (ExerciseManager.prepareAndStartNextExercise()) { // 다음 운동 준비 및 시작
                                shouldProceedToExerciseSet = true
                                exerciseThatWillStart = ExerciseManager.getCurrentExercise()
                                currentRoundTripTime = exerciseThatWillStart?.roundTripTime ?: 0
                                Log.d(TAG, "RESTING -> Next exercise: ${exerciseThatWillStart?.name}, RoundTripTime: $currentRoundTripTime")
                            } else {
                                handleAllExercisesFinished()
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
                            shouldProceedToExerciseSet = false
                        }
                    }

                    // 2. 응답 메시지 구성 및 전송 (운동을 진행해야 하는 경우에만)
                    if (shouldProceedToExerciseSet && exerciseThatWillStart != null) {
                        // 목표 시간을 로그로만 출력하고, 실제 응답에는 아직 포함하지 않음 (원래 코드 방식)
                        Log.i(TAG, "Target roundTripTime for '${exerciseThatWillStart.name}': $currentRoundTripTime seconds.")

                        // 원래 "ACK_START" 응답 (목표 시간 미포함)
//                        val responseToDevice = "ACK_START"
                        // 만약 목표 시간을 포함하려면 아래와 같이 수정:
                         val responseToDevice = "[ACK_START,${currentRoundTripTime}]"
                        // 주의: currentRoundTripTime이 음수일 경우 0으로 처리하는 등의 방어 코드 추가 고려
                        // val nonNegativeTargetTime = if (currentRoundTripTime < 0) 0 else currentRoundTripTime
                        // val responseToDevice = "ACK_START,${nonNegativeTargetTime}"

                        val responseBytes = responseToDevice.toByteArray(Charsets.UTF_8)

                        BleConnectionManager.TARGET_WRITE_CHARACTERISTIC_UUID?.let { writeUuid ->
                            BleConnectionManager.writeCharacteristic(
                                BleConnectionManager.TARGET_SERVICE_UUID,
                                writeUuid,
                                responseBytes,
                                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            )
                            Log.i(TAG, "Attempting to send response to device: '$responseToDevice'")
                        } ?: Log.w(TAG, "TARGET_WRITE_CHARACTERISTIC_UUID is null. Cannot send response.")

                        pendingScreenTransitionToExerciseSet = true
                        Log.d(TAG, "Response send attempt initiated. Will proceed to ExerciseSetActivity after write confirmation.")

                    } else if (shouldProceedToExerciseSet && exerciseThatWillStart == null && ExerciseManager.state != SessionState.FINISHED) {
                        Log.e(TAG, "Error: Should proceed to exercise set, but exerciseThatWillStart is null. State: ${ExerciseManager.state}")
                        infoTextView.text = "운동 정보 오류 발생. 관리자에게 문의하세요."
                    }

                } else {
                    // "START_REQ"가 아닌 다른 데이터 수신 시 처리
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
                } else {
                    Toast.makeText(this, "알림/Indication 설정 실패: $status", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

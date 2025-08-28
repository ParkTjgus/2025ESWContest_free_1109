package com.example.app.ble // 실제 패키지명으로 변경하세요

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattDescriptor
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
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
    private lateinit var tvReceivedData: TextView
    private lateinit var btnReadData: Button

    private var deviceName: String? = null
    private var deviceAddress: String? = null
    private var currentTargetSpeed: Int = 15

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
        tvConnectionState.text = "Status: Initializing..."

        BleConnectionManager.registerListener(this)

        if (BleConnectionManager.connectedDevice?.address == deviceAddress && BleConnectionManager.isConnected()) {
            updateConnectionState(BluetoothProfile.STATE_CONNECTED)
        } else if (BleConnectionManager.connectionState == BluetoothProfile.STATE_CONNECTING && BleConnectionManager.connectedDevice?.address == deviceAddress) {
            updateConnectionState(BluetoothProfile.STATE_CONNECTING)
        } else {
            updateConnectionState(BluetoothProfile.STATE_DISCONNECTED)
        }

        btnReadData.setOnClickListener {
            if (BleConnectionManager.isConnected() && BleConnectionManager.connectedDevice?.address == deviceAddress) {
                BleConnectionManager.readTargetCharacteristic()
            } else {
                Toast.makeText(this, "기기에 연결되어 있지 않거나 대상 기기가 아닙니다.", Toast.LENGTH_SHORT).show()
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
                    btnReadData.isEnabled = true
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    tvConnectionState.text = "Status: Disconnected"
                    btnReadData.isEnabled = false
                    tvReceivedData.text = "연결 끊김"
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    tvConnectionState.text = "Status: Connecting..."
                    btnReadData.isEnabled = false
                }
            }
        }
    }

    override fun onConnectionStateChanged(newState: Int, gatt: BluetoothGatt?) {
        if (gatt?.device?.address == deviceAddress || (deviceAddress == null && newState == BluetoothProfile.STATE_DISCONNECTED)) {
            updateConnectionState(newState)
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        if (gatt?.device?.address == deviceAddress) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread { Toast.makeText(this, "서비스 발견 완료 ($deviceName)", Toast.LENGTH_SHORT).show() }
            } else {
                runOnUiThread { Toast.makeText(this, "서비스 발견 실패: $status", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, value: ByteArray?, status: Int) {
        if (gatt?.device?.address == deviceAddress &&
            (characteristic?.uuid == BleConnectionManager.TARGET_READ_CHARACTERISTIC_UUID || characteristic?.uuid == BleConnectionManager.TARGET_NOTIFY_CHARACTERISTIC_UUID) ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val dataString = value?.let { String(it, Charsets.UTF_8) } ?: "N/A"
                val hexString = value?.joinToString(separator = " ") { String.format("%02X", it) } ?: "N/A"
                runOnUiThread {
                    tvReceivedData.text = "읽은 데이터 (UTF-8): $dataString\n읽은 데이터 (Hex): $hexString"
                    Toast.makeText(this, "읽은 데이터: $dataString", Toast.LENGTH_SHORT).show()
                }
            } else {
                runOnUiThread {
                    tvReceivedData.text = "데이터 읽기 실패 (Status: $status)"
                    Toast.makeText(this, "데이터 읽기 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
        if (gatt?.device?.address == deviceAddress) {
            val charUuid = characteristic?.uuid ?: "Unknown Characteristic"
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread { Toast.makeText(this, "$charUuid 쓰기 성공", Toast.LENGTH_SHORT).show() }
            } else {
                runOnUiThread { Toast.makeText(this, "$charUuid 쓰기 실패: $status", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, value: ByteArray?) {
        if (gatt?.device?.address == deviceAddress && characteristic?.uuid == BleConnectionManager.TARGET_NOTIFY_CHARACTERISTIC_UUID) {
            val dataString = value?.let { String(it, Charsets.UTF_8) } ?: "N/A"
            val hexString = value?.joinToString(separator = " ") { String.format("%02X", it) } ?: "N/A"
            Log.i(TAG, "Notification Received: (UTF-8): '$dataString', (Hex): $hexString")

            runOnUiThread {
                tvReceivedData.text = "알림 수신 (UTF-8): $dataString\n알림 수신 (Hex): $hexString"

                if (dataString == "START_REQ") {
                    Log.d(TAG, "Received 'START_REQ'. Current ExerciseManager state: ${ExerciseManager.state}")

                    val responseToDevice = """["ACK_START",$currentTargetSpeed]"""
                    val responseBytes = responseToDevice.toByteArray(Charsets.UTF_8)

                    BleConnectionManager.TARGET_WRITE_CHARACTERISTIC_UUID?.let { writeUuid ->
                        BleConnectionManager.writeCharacteristic(
                            BleConnectionManager.TARGET_SERVICE_UUID,
                            writeUuid,
                            responseBytes,
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        )
                        Log.i(TAG, "Sent 'ACK_START' response to device: $responseToDevice")
                        Toast.makeText(this, "ACK_START 전송: $responseToDevice", Toast.LENGTH_SHORT).show()
                    } ?: Log.w(TAG, "TARGET_WRITE_CHARACTERISTIC_UUID is null. Cannot send ACK_START.")

                    var proceedToExerciseSet = false
                    when (ExerciseManager.state) { // ⭐ 올바른 SessionState 사용
                        SessionState.IDLE -> {
                            Log.d(TAG, "ExerciseManager is IDLE. Preparing next exercise.")
                            if (ExerciseManager.prepareAndStartNextExercise()) {
                                proceedToExerciseSet = true
                            } else {
                                Log.i(TAG, "All exercises finished according to ExerciseManager.")
                                Toast.makeText(this, "모든 운동이 완료되었습니다!", Toast.LENGTH_LONG).show()
                            }
                        }
                        SessionState.WORKING -> {
                            Log.d(TAG, "ExerciseManager is already WORKING. Proceeding with current exercise: ${ExerciseManager.getCurrentExercise()?.name}")
                            proceedToExerciseSet = true
                        }
                        SessionState.RESTING -> {
                            Log.w(TAG, "Received START_REQ while ExerciseManager is RESTING. Forcing to working.")
                            ExerciseManager.finishRest()
                            if (ExerciseManager.state == SessionState.WORKING) {
                                proceedToExerciseSet = true
                            } else {
                                Log.e(TAG, "Failed to transition from RESTING to WORKING for START_REQ.")
                            }
                        }
                        SessionState.FINISHED -> {
                            Log.i(TAG, "Received START_REQ but ExerciseManager is already FINISHED.")
                            Toast.makeText(this, "모든 운동이 이미 완료되었습니다.", Toast.LENGTH_LONG).show()
                        }
                    }

                    if (proceedToExerciseSet) {
                        Log.d(TAG, "Proceeding to ExerciseSetActivity. ExerciseManager state: ${ExerciseManager.state}")
                        // val exerciseSetIntent = Intent(this@DeviceControlActivity, Class.forName("com.example.app.exercise.ExerciseSetActivity")) // 이전 코드
                        val exerciseSetIntent = Intent(this@DeviceControlActivity, ExerciseSetActivity::class.java) // ⭐ 직접 클래스 참조
                        startActivity(exerciseSetIntent)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: DeviceControlActivity resumed. ExerciseManager state: ${ExerciseManager.state}")
        if (BleConnectionManager.isConnected() && BleConnectionManager.connectedDevice?.address == deviceAddress) {
            updateConnectionState(BluetoothProfile.STATE_CONNECTED)

            // ⭐ 올바른 SessionState 사용
            if (ExerciseManager.state == SessionState.IDLE && ExerciseManager.getCurrentExercise() != null) {
                tvReceivedData.text = "이전 운동 완료. 다음 운동 시작을 위한 START_REQ 대기 중..."
            } else if (ExerciseManager.state == SessionState.FINISHED) {
                tvReceivedData.text = "모든 운동 완료!"
            }
        } else {
            updateConnectionState(BluetoothProfile.STATE_DISCONNECTED)
        }
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
        if (gatt?.device?.address == deviceAddress) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread { Toast.makeText(this, "알림/Indication 설정 완료", Toast.LENGTH_SHORT).show() }
            } else {
                runOnUiThread { Toast.makeText(this, "알림/Indication 설정 실패: $status", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}

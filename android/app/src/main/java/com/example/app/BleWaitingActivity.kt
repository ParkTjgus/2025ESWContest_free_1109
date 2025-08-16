package com.example.app

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class BleWaitingActivity : AppCompatActivity() {

    private val targetMac = "54:90:AC:A8:D5:3C"
    private var bluetoothGatt: BluetoothGatt? = null
    private val bluetoothAdapter by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val bleScanner by lazy { bluetoothAdapter.bluetoothLeScanner }

    private val REQUEST_BLUETOOTH_PERMISSIONS = 1

    private fun ensureBlePermissions() {
        val permissions = mutableListOf<String>()

        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
        }
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), REQUEST_BLUETOOTH_PERMISSIONS)
        }
    }


    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread {
                    Log.d("BleWaiting", "✅ GATT Connected!")

                    // 연결 성공 시 다음 화면으로 이동
                    val intent = Intent(this@BleWaitingActivity, BleConnectedActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread {
                    Log.d("BleWaiting", "❌ GATT Disconnected")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ble_waiting)

        ensureBlePermissions() // ← 권한 먼저 요청
        startBleScan()
    }


    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        val filter = ScanFilter.Builder().setDeviceAddress(targetMac).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner.startScan(listOf(filter), settings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.address == targetMac) {
                Log.d("BleWaiting", "🎯 Found target device: ${result.device.address}")
                bleScanner.stopScan(this)
                bluetoothGatt = result.device.connectGatt(this@BleWaitingActivity, false, gattCallback)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()

        bluetoothGatt?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        bluetoothGatt = null
    }



}

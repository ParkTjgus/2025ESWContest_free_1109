package com.example.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

@SuppressLint("MissingPermission")
class BleSearchingActivity : AppCompatActivity() {

    private var isScanning = false
    private val scanResults = mutableListOf<BluetoothDevice>()
    private lateinit var listAdapter: ArrayAdapter<String>

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val bleScanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    private var bluetoothGatt: BluetoothGatt? = null

    private lateinit var scanButton: Button
    private lateinit var disconnectButton: Button

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.entries.any { !it.value }) {
                Toast.makeText(this, "Permissions are required for BLE scanning.", Toast.LENGTH_SHORT).show()
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) {
                Toast.makeText(this, "Bluetooth must be enabled to scan.", Toast.LENGTH_SHORT).show()
            }
        }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                // 특정 MAC 주소만 처리
                if (device.address == "54:90:AC:A8:D5:3C" && !scanResults.contains(device)) {
                    scanResults.add(device)
                    val deviceInfo = "${device.name ?: "Unknown"} (${device.address})"
                    listAdapter.add(deviceInfo)
                    listAdapter.notifyDataSetChanged()

                    // 스캔 중지하고 바로 연결
                    stopBleScan()
                    connectToDevice(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BleScan", "Scan failed with error code: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            runOnUiThread {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Log.d("BLE", "Connected to GATT server")
                            gatt.discoverServices()
                            disconnectButton.isEnabled = true
                            scanButton.isEnabled = false
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Log.d("BLE", "Disconnected from GATT server")
                            disconnectButton.isEnabled = false
                            scanButton.isEnabled = true
                        }
                    }
                } else {
                    Log.e("BLE", "Connection failed with status $status")
                    disconnectButton.isEnabled = false
                    scanButton.isEnabled = true
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "Services discovered: ${gatt.services.size}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ble_searching)

        scanButton = findViewById(R.id.scan_button)
        disconnectButton = findViewById<Button>(R.id.disconnect_button).apply {
            isEnabled = false
            setOnClickListener {
                bluetoothGatt?.disconnect()
            }
        }

        scanButton.setOnClickListener { toggleScan() }

        val scanResultsListView: ListView = findViewById(R.id.scan_results_list)
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        scanResultsListView.adapter = listAdapter
    }

    override fun onResume() {
        super.onResume()
        if (!hasPermissions(requiredPermissions)) {
            requestPermissionLauncher.launch(requiredPermissions)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isScanning) stopBleScan()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    private fun toggleScan() {
        if (isScanning) stopBleScan() else startBleScan()
    }

    private fun startBleScan() {
        if (!hasPermissions(requiredPermissions)) {
            requestPermissionLauncher.launch(requiredPermissions)
            return
        }
        if (bluetoothAdapter?.isEnabled == false) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        scanResults.clear()
        listAdapter.clear()
        listAdapter.notifyDataSetChanged()

        // MAC 주소 필터
        val filter = ScanFilter.Builder()
            .setDeviceAddress("54:90:AC:A8:D5:3C")
            .build()
        val filters = listOf(filter)

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(filters, settings, scanCallback)
        isScanning = true
        scanButton.text = "Stop Scan"
        Log.d("BleScan", "BLE scan started for 54:90:AC:A8:D5:3C")
    }

    private fun stopBleScan() {
        bleScanner?.stopScan(scanCallback)
        isScanning = false
        scanButton.text = "Start Scan"
        Log.d("BleScan", "BLE scan stopped.")
    }

    private fun connectToDevice(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
        Toast.makeText(this, "Connecting to ${device.name ?: "Unknown"}", Toast.LENGTH_SHORT).show()
        Log.d("BLE", "Connecting to ${device.address}")
    }

    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}

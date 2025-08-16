package com.example.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

@SuppressLint("MissingPermission") // 권한 확인 로직이 있으므로 Lint 경고 무시
class BleSearchingActivity : AppCompatActivity() {

    //region 변수 선언
    private var isScanning = false
    private val scanResults = mutableListOf<BluetoothDevice>()
    private lateinit var listAdapter: ArrayAdapter<String>

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private val bleScanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    //endregion

    //region ActivityResultLaunchers
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
    //endregion

    //region 생명주기 콜백
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ble_searching)
        setupWindowInsets()

        val scanButton: Button = findViewById(R.id.scan_button)
        scanButton.setOnClickListener {
            toggleScan()
        }

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
        if (isScanning) {
            stopBleScan()
        }
    }
    //endregion

    //region 스캔 로직
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                if (!scanResults.contains(device) && device.name != null) {
                    scanResults.add(device)
                    val deviceInfo = "${device.name} (${device.address})"
                    listAdapter.add(deviceInfo)
                    listAdapter.notifyDataSetChanged()
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BleScan", "Scan failed with error code: $errorCode")
        }
    }

    private fun toggleScan() {
        if (isScanning) {
            stopBleScan()
        } else {
            startBleScan()
        }
    }


    private fun startBleScan() {
        if (!hasPermissions(requiredPermissions)) {
            Log.d("BleScan", "Permissions not granted yet.")
            requestPermissionLauncher.launch(requiredPermissions)
            return
        }

        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
            return
        }

        scanResults.clear()
        listAdapter.clear()
        listAdapter.notifyDataSetChanged()

        // ScanSettings 기본 설정 (빠른 스캔)
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // 모든 BLE 기기 스캔 → 필터는 빈 리스트 사용
        bleScanner?.startScan(emptyList<ScanFilter>(), settings, scanCallback)

        isScanning = true
        findViewById<Button>(R.id.scan_button).text = "Stop Scan"
        Log.d("BleScan", "BLE scan started.")
    }

    private fun stopBleScan() {
        bleScanner?.stopScan(scanCallback)
        isScanning = false
        findViewById<Button>(R.id.scan_button).text = "Start Scan"
        Log.d("BleScan", "BLE scan stopped.")
    }
    //endregion

    //region 유틸리티 함수
    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun setupWindowInsets() {
        val mainView = findViewById<android.view.View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    //endregion
}
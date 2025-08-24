package com.example.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.app.R // R 클래스의 실제 패키지명으로 수정해야 할 수 있습니다.

class BleScanActivity : AppCompatActivity() {

    private lateinit var devicesRecyclerView: RecyclerView
    private lateinit var scanButton: Button
    private lateinit var bleDeviceListAdapter: BleDeviceListAdapter
    private val discoveredDevices = mutableListOf<BluetoothDevice>()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD: Long = 10000 // 10초 후 스캔 중지

    // 권한 요청 결과 처리
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions: Map<String, Boolean> ->
            var allGranted = true
            permissions.entries.forEach { entry ->
                if (!entry.value) {
                    allGranted = false
                    Log.w("BleScanActivity", "Permission not granted: ${entry.key}")
                }
            }
            if (allGranted) {
                Log.d("BleScanActivity", "All permissions granted.")
                initializeBluetoothAndAttemptScan()
            } else {
                Toast.makeText(this, "Permissions are required for BLE scanning.", Toast.LENGTH_LONG).show()
            }
        }

    // Bluetooth 활성화 요청 결과 처리
    private val requestEnableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.d("BleScanActivity", "Bluetooth enabled by user.")
                initBluetoothScanner() // 스캐너 초기화
                // 사용자가 스캔 버튼을 다시 누르도록 유도하거나, 자동으로 스캔 시작 가능
                // scanButton.performClick()
            } else {
                Log.w("BleScanActivity", "Bluetooth not enabled by user.")
                Toast.makeText(this, "Bluetooth must be enabled to scan for devices.", Toast.LENGTH_LONG).show()
            }
        }

    // BLE 스캔 콜백
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission") // BLUETOOTH_CONNECT 권한은 이미 확인됨
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.device?.let { device ->
                bleDeviceListAdapter.addDevice(device)
                // Log.d("BleScanActivity", "Device found: ${device.name ?: "Unknown"} (${device.address})")
            }
        }

        @SuppressLint("MissingPermission") // BLUETOOTH_CONNECT 권한은 이미 확인됨
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            results?.forEach { result ->
                result.device?.let { device ->
                    bleDeviceListAdapter.addDevice(device)
                    // Log.d("BleScanActivity", "Device found (batch): ${device.name ?: "Unknown"} (${device.address})")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("BleScanActivity", "BLE Scan Failed with error code: $errorCode")
            Toast.makeText(this@BleScanActivity, "BLE Scan Failed: $errorCode", Toast.LENGTH_LONG).show()
            isScanning = false
            scanButton.text = "Start Scan"
            handler.removeCallbacksAndMessages(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ble_scan)

        devicesRecyclerView = findViewById(R.id.devicesRecyclerView)
        scanButton = findViewById(R.id.scanButton)

        setupRecyclerView()

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        scanButton.setOnClickListener {
            initializeBluetoothAndAttemptScan()
        }
    }

    private fun initializeBluetoothAndAttemptScan() {
        if (checkAndRequestPermissions()) {
            if (bluetoothAdapter?.isEnabled == true) {
                initBluetoothScanner() // 스캐너가 초기화되었는지 확인 및 초기화
                if (bluetoothLeScanner != null) {
                    if (!isScanning) {
                        startBleScan()
                    } else {
                        stopBleScan()
                    }
                } else {
                    Toast.makeText(this, "BLE Scanner not available.", Toast.LENGTH_LONG).show()
                }
            } else {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                requestEnableBluetoothLauncher.launch(enableBtIntent)
            }
        }
    }


    private fun initBluetoothScanner() {
        if (bluetoothLeScanner == null && bluetoothAdapter?.isEnabled == true) {
            bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
            if (bluetoothLeScanner == null) {
                Log.e("BleScanActivity", "Failed to get BluetoothLeScanner.")
                Toast.makeText(this, "Failed to initialize BLE scanner.", Toast.LENGTH_SHORT).show()
            } else {
                Log.d("BleScanActivity", "BluetoothLeScanner initialized.")
            }
        }
    }

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
                // AndroidManifest.xml에서 BLUETOOTH_SCAN에 대해 neverForLocation을 사용했다면
                // ACCESS_FINE_LOCATION은 필수가 아닐 수 있습니다.
                // 하지만, 안정성을 위해 또는 특정 상황(Beacon 등)을 위해 필요할 수 있습니다.
                // Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                // Manifest.permission.BLUETOOTH, // API 30 이하에서는 자동 부여되는 경우가 많음
                // Manifest.permission.BLUETOOTH_ADMIN, // API 30 이하에서는 자동 부여되는 경우가 많음
                Manifest.permission.ACCESS_FINE_LOCATION // API 30 이하 스캔에 필수
            )
        }


    private fun checkAndRequestPermissions(): Boolean {
        if (requiredPermissions.isEmpty() && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // API 23 미만에서는 런타임 권한이 없고, S 이상이 아닐 때 requiredPermissions가 비어있으면 통과
            return true
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        return if (missingPermissions.isEmpty()) {
            Log.d("BleScanActivity", "All required permissions are already granted.")
            true
        } else {
            Log.d("BleScanActivity", "Requesting missing permissions: ${missingPermissions.joinToString()}")
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
            false
        }
    }

    private fun setupRecyclerView() {
        bleDeviceListAdapter = BleDeviceListAdapter(discoveredDevices) { device ->
            try {
                // 아이템 클릭 시 처리 로직
                val deviceName = device.name ?: "Unknown Device" // BLUETOOTH_CONNECT 필요
                val deviceAddress = device.address             // BLUETOOTH_CONNECT 필요 (일부 상황)
                Log.d("BleScanActivity", "Clicked on device: $deviceName - $deviceAddress")
                Toast.makeText(this, "Clicked: $deviceName", Toast.LENGTH_SHORT).show()
                // TODO: 기기 연결 로직으로 이동
                if (isScanning) {
                    stopBleScan() // 기기 선택 시 스캔 중지
                }
            } catch (e: SecurityException) {
                Log.e("BleScanActivity", "SecurityException when accessing device info: ${e.message}")
                Toast.makeText(this, "Permission issue: Cannot access device details.", Toast.LENGTH_LONG).show()
                checkAndRequestPermissions() // 권한 재요청 시도
            }
        }
        devicesRecyclerView.adapter = bleDeviceListAdapter
        devicesRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    @SuppressLint("MissingPermission") // BLUETOOTH_SCAN 권한은 checkAndRequestPermissions에서 확인
    private fun startBleScan() {
        if (isScanning) return
        if (bluetoothLeScanner == null) {
            Log.e("BleScanActivity", "BluetoothLeScanner not initialized. Cannot start scan.")
            Toast.makeText(this, "Scanner not ready. Please try again.", Toast.LENGTH_SHORT).show()
            return
        }

        Log.i("BleScanActivity", "Starting BLE Scan.")
        discoveredDevices.clear()
        bleDeviceListAdapter.clearDevices()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // 스캔 모드 설정
            .build()

        // 스캔 필터 (선택 사항 - 특정 서비스 UUID를 가진 기기만 스캔 등)
        // val scanFilters: List<ScanFilter> = listOf(
        // ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString("YOUR_SERVICE_UUID_HERE"))).build()
        // )
        // bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)

        bluetoothLeScanner?.startScan(null, scanSettings, scanCallback) // 필터 없이 스캔

        isScanning = true
        scanButton.text = "Stop Scan"
        Toast.makeText(this, "Scanning for devices...", Toast.LENGTH_SHORT).show()

        handler.postDelayed({
            if (isScanning) {
                Log.i("BleScanActivity", "Scan timed out.")
                stopBleScan()
            }
        }, SCAN_PERIOD)
    }

    @SuppressLint("MissingPermission") // BLUETOOTH_SCAN 권한은 checkAndRequestPermissions에서 확인
    private fun stopBleScan() {
        if (!isScanning && bluetoothLeScanner == null) { // 이미 중지되었거나 스캐너가 없으면 반환
            if (bluetoothLeScanner == null) Log.w("BleScanActivity", "stopBleScan called but scanner is null")
            isScanning = false // 확실히 상태 업데이트
            scanButton.text = "Start Scan"
            handler.removeCallbacksAndMessages(null)
            return
        }
        if (!isScanning) return // 이미 중지된 경우 추가 확인

        Log.i("BleScanActivity", "Stopping BLE Scan.")
        bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        scanButton.text = "Start Scan"
        handler.removeCallbacksAndMessages(null) // 스캔 타임아웃 콜백 제거
        Toast.makeText(this, "Scan stopped.", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        // 블루투스가 활성화되어 있고, 권한이 있다면 스캐너 초기화
        if (checkAndRequestPermissions() && bluetoothAdapter?.isEnabled == true) {
            initBluetoothScanner()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isScanning) {
            stopBleScan()
        }
    }
}

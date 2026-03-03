package com.example.antilost

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.antilost.ui.theme.AntiLostAppTheme
import java.util.UUID

class MainActivity : ComponentActivity() {

    private var scanner: BluetoothLeScanner? = null
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var bluetoothAdapter: BluetoothAdapter
    
    private val viewModel: AntiLostViewModel by viewModels()
    private val targetDeviceName = "ESP32_AntiLost"
    
    // BLE GATT - ใช้ persistent connection
    private var bluetoothGatt: BluetoothGatt? = null
    private var targetCharacteristic: BluetoothGattCharacteristic? = null
    private var isSendingBeep = false // flag เพื่อป้องกันการทำงานซ้อนทับ
    private var disconnectTimer: Runnable? = null
    private var connectionRetryCount = 0 // นับจำนวนครั้งที่ลองเชื่อมต่อใหม่
    private val MAX_RETRY_COUNT = 3 // ลองสูงสุด 3 ครั้ง
    
    // UUID ต้องตรงกับใน ESP32
    private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager =
            getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        bluetoothAdapter = bluetoothManager.adapter ?: run {
            Toast.makeText(this, "อุปกรณ์ไม่รองรับ Bluetooth", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        scanner = bluetoothAdapter.bluetoothLeScanner

        mediaPlayer = MediaPlayer.create(
            this,
            Settings.System.DEFAULT_ALARM_ALERT_URI
        )

        // อัพเดทสถานะ Bluetooth
        viewModel.updateBluetoothStatus(bluetoothAdapter.isEnabled)

        // ตั้งค่า Compose UI
        setContent {
            AntiLostAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    
                    AntiLostScreen(
                        uiState = uiState,
                        onStartScan = { checkPermissionAndStart() },
                        onStopScan = { stopScan() },
                        onThresholdChange = { threshold ->
                            viewModel.updateRssiThreshold(threshold)
                        },
                        onFindDevice = { sendBeepInFindMode() },
                        onSwitchMode = { mode -> switchMode(mode) },
                        onConnectDevice = { connectToDeviceInFindMode() },
                        onDisconnectDevice = { disconnectFromDevice() }
                    )
                }
            }
        }
    }

    private fun checkPermissionAndStart() {
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "กรุณาเปิด Bluetooth", Toast.LENGTH_LONG).show()
            viewModel.updateBluetoothStatus(false)
            return
        }

        viewModel.updateBluetoothStatus(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            val scanPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            )

            val connectPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            )

            if (scanPermission != PackageManager.PERMISSION_GRANTED ||
                connectPermission != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ),
                    100
                )
                return
            }
        }

        startScan()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100) {
            startScan()
        }
    }

    @Suppress("MissingPermission")
    private fun startScan() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        viewModel.startScanning()
        viewModel.clearDevices()
        scanner?.startScan(scanCallback)
        Toast.makeText(this, "เริ่มสแกนอุปกรณ์", Toast.LENGTH_SHORT).show()
    }
    
    @Suppress("MissingPermission")
    private fun stopScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        viewModel.stopScanning()
        scanner?.stopScan(scanCallback)
        
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            mediaPlayer.prepare()
            viewModel.setAlarmPlaying(false)
        }
        
        Toast.makeText(this, "หยุดสแกน", Toast.LENGTH_SHORT).show()
    }

    private val scanCallback = object : ScanCallback() {

        @Suppress("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) return
            }

            val name = result.device.name ?: return
            val address = result.device.address
            val rssi = result.rssi

            // สร้าง DeviceInfo
            val deviceInfo = DeviceInfo(
                name = name,
                address = address,
                rssi = rssi
            )

            // เพิ่มหรืออัพเดทอุปกรณ์ในรายการ
            viewModel.addOrUpdateDevice(deviceInfo)

            // ตรวจสอบว่าเป็นอุปกรณ์เป้าหมายหรือไม่
            if (name == targetDeviceName) {
                // ดึงค่า smoothedRssi จาก device ที่ผ่านการ filter แล้ว
                val currentDevice = viewModel.uiState.value.nearbyDevices.find { it.address == address }
                val smoothedRssi = currentDevice?.smoothedRssi ?: rssi
                
                viewModel.updateTargetDevice(deviceInfo)
                
                // ⚠️ แจ้งเตือนเฉพาะใน ALARM_MODE เท่านั้น
                if (viewModel.uiState.value.currentMode == AppMode.ALARM_MODE) {
                    val threshold = viewModel.uiState.value.rssiThreshold

                    // ใช้ smoothedRssi แทน rssi เพื่อลดความผันผวน
                    if (smoothedRssi < threshold) {
                        if (!mediaPlayer.isPlaying) {
                            try {
                                mediaPlayer.start()
                                viewModel.setAlarmPlaying(true)
                                Toast.makeText(
                                    this@MainActivity,
                                    "⚠️ สัญญาณอ่อน! อาจอยู่ไกลเกินไป",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } else {
                        if (mediaPlayer.isPlaying) {
                            mediaPlayer.stop()
                            mediaPlayer.prepare()
                            viewModel.setAlarmPlaying(false)
                        }
                    }
                }
            }
        }
    }

    @Suppress("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                scanner?.stopScan(scanCallback)
            }
        } else {
            scanner?.stopScan(scanCallback)
        }

        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        mediaPlayer.release()
        
        // ปิดการเชื่อมต่อ GATT
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
    
    // GATT Callback สำหรับจัดการการเชื่อมต่อ BLE (Quick Connect Mode)
    private val gattCallback = object : BluetoothGattCallback() {
        @Suppress("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "🔗 เชื่อมต่อแล้ว...", Toast.LENGTH_SHORT).show()
                    }
                    // ค้นหา services
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    targetCharacteristic = null
                    gatt?.close()
                    
                    // เริ่ม scan ใหม่ทันทีหลังจากตัดการเชื่อมต่อ
                    android.os.Handler(mainLooper).postDelayed({
                        isSendingBeep = false
                        if (viewModel.uiState.value.isScanning) {
                            restartScanAfterGatt()
                        }
                    }, 300) // รอแค่ 300ms
                }
            }
        }
        
        @Suppress("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // หา service และ characteristic
                val service = gatt?.getService(SERVICE_UUID)
                targetCharacteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                
                if (targetCharacteristic != null) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "🔊 พบ Service แล้ว กำลังส่งคำสั่ง...", Toast.LENGTH_SHORT).show()
                    }
                    
                    // ส่งคำสั่ง BEEP หลังรอเล็กน้อย
                    android.os.Handler(mainLooper).postDelayed({
                        sendBeepCommandNow(gatt)
                        
                        // ตั้ง timeout fallback (ในกรณีที่ไม่มี callback)
                        disconnectTimer = Runnable {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "⏱️ ส่งคำสั่งแล้ว (timeout)", Toast.LENGTH_SHORT).show()
                            }
                            gatt?.disconnect()
                        }
                        android.os.Handler(mainLooper).postDelayed(disconnectTimer!!, 2000)
                    }, 300)
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "❌ ไม่พบ Service", Toast.LENGTH_SHORT).show()
                    }
                    gatt?.disconnect()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "❌ Discover Services ล้มเหลว", Toast.LENGTH_SHORT).show()
                }
                gatt?.disconnect()
            }
        }
        
        @Suppress("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            // ยกเลิก timeout fallback
            disconnectTimer?.let { timer ->
                android.os.Handler(mainLooper).removeCallbacks(timer)
            }
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "✅ ส่ง Beep สำเร็จ!", Toast.LENGTH_SHORT).show()
                }
                
                // รอให้ ESP32 ประมวลผลคำสั่งก่อน disconnect (800ms)
                android.os.Handler(mainLooper).postDelayed({
                    gatt?.disconnect()
                }, 800)
            } else {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "❌ ส่งคำสั่งไม่สำเร็จ (status: $status)", Toast.LENGTH_SHORT).show()
                }
                
                // ลองส่งอีกครั้งหรือ disconnect
                android.os.Handler(mainLooper).postDelayed({
                    gatt?.disconnect()
                }, 500)
            }
        }
    }
    
    // เริ่ม scan ใหม่หลังใช้ GATT
    @Suppress("MissingPermission")
    private fun restartScanAfterGatt() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }
        
        try {
            scanner?.startScan(scanCallback)
            Toast.makeText(this, "🔄 กลับสู่โหมดตรวจสอบระยะทาง", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // ส่งคำสั่ง BEEP ทันที (ใช้ NO_RESPONSE เพื่อความเร็วและความเสถียร)
    @Suppress("MissingPermission")
    private fun sendBeepCommandNow(gatt: BluetoothGatt?) {
        if (targetCharacteristic == null) {
            runOnUiThread {
                Toast.makeText(this, "❌ Characteristic ยังไม่พร้อม", Toast.LENGTH_SHORT).show()
            }
            gatt?.disconnect()
            return
        }
        
        try {
            val command = "BEEP".toByteArray()
            
            // ตรวจสอบ write type ที่รองรับ
            val writeType = if (targetCharacteristic!!.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = gatt?.writeCharacteristic(
                    targetCharacteristic!!,
                    command,
                    writeType
                )
                
                if (result == BluetoothGatt.GATT_SUCCESS) {
                    runOnUiThread {
                        Toast.makeText(this, "📤 กำลังส่งคำสั่ง BEEP...", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "❌ Write ล้มเหลว", Toast.LENGTH_SHORT).show()
                    }
                    gatt?.disconnect()
                }
            } else {
                @Suppress("DEPRECATION")
                targetCharacteristic?.writeType = writeType
                @Suppress("DEPRECATION")
                targetCharacteristic?.value = command
                @Suppress("DEPRECATION")
                val success = gatt?.writeCharacteristic(targetCharacteristic!!)
                
                if (success == true) {
                    runOnUiThread {
                        Toast.makeText(this, "📤 กำลังส่งคำสั่ง BEEP...", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "❌ Write ล้มเหลว", Toast.LENGTH_SHORT).show()
                    }
                    gatt?.disconnect()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "❌ Exception: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            gatt?.disconnect()
        }
    }
    
    // เชื่อมต่อกับ ESP32 แบบรวดเร็ว
    @Suppress("MissingPermission")
    private fun quickConnectAndBeep() {
        val targetDevice = viewModel.uiState.value.targetDevice
        
        if (targetDevice == null) {
            Toast.makeText(this, "❌ ไม่พบอุปกรณ์เป้าหมาย", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "❌ ไม่มีสิทธิ์เชื่อมต่อ Bluetooth", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        try {
            // ปิด GATT เดิม (ถ้ามี)
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            targetCharacteristic = null
            
            // เชื่อมต่อใหม่
            val device = bluetoothAdapter.getRemoteDevice(targetDevice.address)
            bluetoothGatt = device.connectGatt(this, false, gattCallback)
            
            Toast.makeText(this, "⏳ กำลังเชื่อมต่อ...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "❌ ไม่สามารถเชื่อมต่อได้: ${e.message}", Toast.LENGTH_SHORT).show()
            isSendingBeep = false
            // เริ่ม scan ใหม่ถ้าเกิด error
            if (viewModel.uiState.value.isScanning) {
                restartScanAfterGatt()
            }
        }
    }
    
    // ฟังก์ชันหาอุปกรณ์ - หยุด scan ชั่วคราว เชื่อมต่อเร็ว ส่ง BEEP แล้วกลับมา scan ต่อ
    @Suppress("MissingPermission")
    private fun findDevice() {
        // ป้องกันการกดซ้ำ
        if (isSendingBeep) {
            Toast.makeText(this, "⏳ กำลังส่งคำสั่ง กรุณารอสักครู่...", Toast.LENGTH_SHORT).show()
            return
        }
        
        val targetDevice = viewModel.uiState.value.targetDevice
        if (targetDevice == null) {
            Toast.makeText(this, "❌ ไม่พบอุปกรณ์เป้าหมาย", Toast.LENGTH_SHORT).show()
            return
        }
        
        // ตรวจสอบสิทธิ์
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "❌ ไม่มีสิทธิ์ใช้งาน Bluetooth", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        isSendingBeep = true
        
        // หยุด scan ชั่วคราว (เพื่อให้ GATT connection เสถียร)
        val wasScanning = viewModel.uiState.value.isScanning
        if (wasScanning) {
            try {
                scanner?.stopScan(scanCallback)
                Toast.makeText(this, "⏸️ พักการสแกนชั่วคราว...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // รอ 200ms แล้วเชื่อมต่อ (ให้ scan หยุดสนิท)
        android.os.Handler(mainLooper).postDelayed({
            quickConnectAndBeep()
        }, 200)
    }
    
    // ========== ฟังก์ชันสำหรับสลับโหมด ==========
    @Suppress("MissingPermission")
    private fun switchMode(mode: AppMode) {
        val currentMode = viewModel.uiState.value.currentMode
        
        if (currentMode == mode) {
            // โหมดเดียวกัน ไม่ต้องทำอะไร
            return
        }
        
        when (mode) {
            AppMode.ALARM_MODE -> {
                // สลับไปโหมดแจ้งเตือน
                Toast.makeText(this, "📡 สลับเป็นโหมดแจ้งเตือนของหาย", Toast.LENGTH_SHORT).show()
                
                // ตัดการเชื่อมต่อ GATT (ถ้ามี)
                if (viewModel.uiState.value.isGattConnected) {
                    disconnectFromDevice()
                }
                
                // อัปเดต UI
                viewModel.switchMode(AppMode.ALARM_MODE)
                
                // ⚠️ เปิด scan กลับเพื่อให้ระบบแจ้งเตือนทำงาน
                if (!viewModel.uiState.value.isScanning) {
                    android.os.Handler(mainLooper).postDelayed({
                        viewModel.startScanning()
                        viewModel.clearDevices()
                        scanner?.startScan(scanCallback)
                        Toast.makeText(this, "✅ เปิดการสแกนแล้ว", Toast.LENGTH_SHORT).show()
                    }, 300) // รอ 300ms ให้ GATT ปิดสนิท
                }
            }
            
            AppMode.FIND_MODE -> {
                // สลับไปโหมดหาของ
                Toast.makeText(this, "🔍 สลับเป็นโหมดหาของ", Toast.LENGTH_SHORT).show()
                
                // ⚠️ ไม่หยุด scan เพื่อให้สามารถตรวจจับอุปกรณ์ได้ต่อเนื่อง
                // แต่จะไม่แจ้งเตือนอัตโนมัติ (เช็คโดย currentMode == AppMode.ALARM_MODE ใน scanCallback)
                
                // หยุดเสียงแจ้งเตือน (ถ้ามี)
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                    mediaPlayer.prepare()
                    viewModel.setAlarmPlaying(false)
                }
                
                // อัปเดต UI
                viewModel.switchMode(AppMode.FIND_MODE)
            }
        }
    }
    
    // ========== ฟังก์ชันสำหรับโหมดหาของ (FIND_MODE) ==========
    
    // เชื่อมต่อแบบ persistent ในโหมดหาของ
    @Suppress("MissingPermission")
    private fun connectToDeviceInFindMode() {
        val targetDevice = viewModel.uiState.value.targetDevice
        
        if (targetDevice == null) {
            Toast.makeText(this, "❌ ไม่พบอุปกรณ์ ESP32_AntiLost", Toast.LENGTH_SHORT).show()
            Toast.makeText(this, "💡 กรุณากดปุ่ม'สแกนหาอุปกรณ์'ก่อน", Toast.LENGTH_LONG).show()
            return
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "❌ ไม่มีสิทธิ์เชื่อมต่อ Bluetooth", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        try {
            // ⚠️ หยุด scan อย่างสมบูรณ์ก่อนเชื่อลต่อ GATT
            val wasScanning = viewModel.uiState.value.isScanning
            
            // ปิด GATT เดิมก่อน
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            targetCharacteristic = null
            
            if (wasScanning) {
                // หยุด scan และรอให้หยุดสนิท
                scanner?.stopScan(scanCallback)
                viewModel.stopScanning()
                Toast.makeText(this, "⏸️ หยุด scan...", Toast.LENGTH_SHORT).show()
                
                // รอ 1 วินาทีเต็ม (เพิ่มจาก 500ms)
                android.os.Handler(mainLooper).postDelayed({
                    connectionRetryCount = 0
                    performGattConnection(targetDevice)
                }, 1000)
            } else {
                connectionRetryCount = 0
                performGattConnection(targetDevice)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "❌ ไม่สามารถเชื่อมต่อได้: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ฟังก์ชันจริงเชื่อมต่อ GATT
    @Suppress("MissingPermission")
    private fun performGattConnection(targetDevice: DeviceInfo) {
        try {
            // เชื่อมต่อแบบ Direct Connection (autoConnect = false)
            val device = bluetoothAdapter.getRemoteDevice(targetDevice.address)
            
            // ล้าง GATT cache (แก้ error 133)
            try {
                val refreshMethod = device.javaClass.getMethod("refresh")
                refreshMethod.invoke(device)
                Toast.makeText(this, "🧹 ล้าง cache...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // Ignore if refresh fails
            }
            
            // เชื่อมต่อด้วย Direct Connection
            bluetoothGatt = device.connectGatt(
                this, 
                false,  // autoConnect = false (เชื่อมต่อทันที)
                gattCallbackFindMode,
                BluetoothDevice.TRANSPORT_LE // บังคับใช้ BLE เท่านั้น
            )
            
            val retryText = if (connectionRetryCount > 0) " (ครั้งที่ ${connectionRetryCount + 1})" else ""
            Toast.makeText(this, "⏳ กำลังเชื่อมต่อ$retryText...", Toast.LENGTH_SHORT).show()
            
            // ตั้ง timeout 10 วินาที (direct connection เร็วกว่า autoConnect)
            android.os.Handler(mainLooper).postDelayed({
                if (!viewModel.uiState.value.isGattConnected) {
                    Toast.makeText(this, "⏱️ Timeout - ลองใหม่...", Toast.LENGTH_SHORT).show()
                    bluetoothGatt?.disconnect()
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    retryConnection(targetDevice)
                }
            }, 10000)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "❌ Exception: ${e.message}", Toast.LENGTH_SHORT).show()
            retryConnection(targetDevice)
        }
    }
    
    // ฟังก์ชัน Retry การเชื่อมต่อ
    @Suppress("MissingPermission")
    private fun retryConnection(targetDevice: DeviceInfo) {
        if (connectionRetryCount < MAX_RETRY_COUNT) {
            connectionRetryCount++
            Toast.makeText(this, "🔄 ลองครั้งที่ $connectionRetryCount/$MAX_RETRY_COUNT...", Toast.LENGTH_SHORT).show()
            
            // รอ 2 วินาทีแล้วลองใหม่
            android.os.Handler(mainLooper).postDelayed({
                performGattConnection(targetDevice)
            }, 2000)
        } else {
            Toast.makeText(this, "❌ เชื่อมต่อไม่ได้ ($MAX_RETRY_COUNT ครั้ง)", Toast.LENGTH_LONG).show()
            
            // แสดงคำใบ้แนะนำ
            val message = """
                💡 ลองวิธีแก้:
                1. รีสตาร์ท ESP32 (กดปุ่ม RESET)
                2. ปิด-เปิด Bluetooth ในมือถือ
                3. เข้าใกล้ ESP32 (< 1 เมตร)
                4. ปิดแอพ Bluetooth อื่นที่ใช้งานอยู่
            """.trimIndent()
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            
            connectionRetryCount = 0
        }
    }
    
    // ตัดการเชื่อมต่อ
    @Suppress("MissingPermission")
    private fun disconnectFromDevice() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            targetCharacteristic = null
            viewModel.setGattConnected(false)
            connectionRetryCount = 0 // รีเซ็ต retry counter
            
            Toast.makeText(this, "✂️ ตัดการเชื่อมต่อแล้ว", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // ส่ง BEEP ในโหมดหาของ (ไม่หยุด scan)
    @Suppress("MissingPermission")
    private fun sendBeepInFindMode() {
        if (!viewModel.uiState.value.isGattConnected || targetCharacteristic == null) {
            Toast.makeText(this, "❌ กรุณาเชื่อมต่อกับอุปกรณ์ก่อน", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val command = "BEEP".toByteArray()
            
            // ตรวจสอบ write type ที่รองรับ
            val writeType = if (targetCharacteristic!!.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = bluetoothGatt?.writeCharacteristic(
                    targetCharacteristic!!,
                    command,
                    writeType
                )
                
                if (result == BluetoothGatt.GATT_SUCCESS) {
                    Toast.makeText(this, "📤 กำลังส่งคำสั่ง BEEP...", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "❌ ส่งคำสั่งไม่สำเร็จ", Toast.LENGTH_SHORT).show()
                }
            } else {
                @Suppress("DEPRECATION")
                targetCharacteristic?.writeType = writeType
                @Suppress("DEPRECATION")
                targetCharacteristic?.value = command
                @Suppress("DEPRECATION")
                val success = bluetoothGatt?.writeCharacteristic(targetCharacteristic!!)
                
                if (success == true) {
                    Toast.makeText(this, "📤 กำลังส่งคำสั่ง BEEP...", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "❌ ส่งคำสั่งไม่สำเร็จ", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "❌ เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // GATT Callback สำหรับโหมดหาของ (Persistent Connection)
    private val gattCallbackFindMode = object : BluetoothGattCallback() {
        @Suppress("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        connectionRetryCount = 0 // รีเซ็ตเมื่อเชื่อมต่อสำเร็จ
                        runOnUiThread {
                            viewModel.setGattConnected(true)
                            Toast.makeText(this@MainActivity, "✅ เชื่อมต่อสำเร็จ!", Toast.LENGTH_SHORT).show()
                        }
                        // ค้นหา services
                        gatt?.discoverServices()
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "❌ เชื่อมต่อล้มเหลว (status: $status)", Toast.LENGTH_SHORT).show()
                        }
                        gatt?.close()
                        
                        // ลองเชื่อมต่อใหม่ถ้าเป็น error 133
                        if (status == 133) {
                            val targetDevice = viewModel.uiState.value.targetDevice
                            if (targetDevice != null) {
                                runOnUiThread {
                                    android.os.Handler(mainLooper).postDelayed({
                                        retryConnection(targetDevice)
                                    }, 1000)
                                }
                            }
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    runOnUiThread {
                        viewModel.setGattConnected(false)
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Toast.makeText(this@MainActivity, "⚠️ ตัดการเชื่อมต่อ", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "❌ การเชื่อมต่อล้มเหลว (status: $status)", Toast.LENGTH_SHORT).show()
                        }
                    }
                    targetCharacteristic = null
                    gatt?.close()
                }
            }
        }
        
        @Suppress("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(SERVICE_UUID)
                targetCharacteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                
                if (targetCharacteristic != null) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "🔊 พร้อมส่งสัญญาณ Beep!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "❌ ไม่พบ Service/Characteristic", Toast.LENGTH_SHORT).show()
                    }
                    // ตัดการเชื่อมต่อถ้าไม่พบ service
                    gatt?.disconnect()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "❌ Discover Services ล้มเหลว (status: $status)", Toast.LENGTH_SHORT).show()
                }
                gatt?.disconnect()
            }
        }
        
        @Suppress("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "✅ ส่ง Beep สำเร็จ!", Toast.LENGTH_SHORT).show()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "❌ ส่งคำสั่งไม่สำเร็จ (status: $status)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
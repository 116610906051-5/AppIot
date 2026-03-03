package com.example.antilost

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UiState(
    val isScanning: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val targetDevice: DeviceInfo? = null,
    val nearbyDevices: List<DeviceInfo> = emptyList(),
    val rssiThreshold: Int = -80,
    val isAlarmPlaying: Boolean = false,
    val currentMode: AppMode = AppMode.ALARM_MODE, // โหมดเริ่มต้น: แจ้งเตือนของหาย
    val isGattConnected: Boolean = false // สถานะการเชื่อมต่อ GATT (ใช้ในโหมดหาของ)
)

class AntiLostViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    fun updateBluetoothStatus(isEnabled: Boolean) {
        _uiState.value = _uiState.value.copy(isBluetoothEnabled = isEnabled)
    }
    
    fun startScanning() {
        _uiState.value = _uiState.value.copy(isScanning = true)
    }
    
    fun stopScanning() {
        _uiState.value = _uiState.value.copy(isScanning = false)
    }
    
    fun updateTargetDevice(device: DeviceInfo?) {
        _uiState.value = _uiState.value.copy(targetDevice = device)
    }
    
    fun addOrUpdateDevice(device: DeviceInfo) {
        val currentDevices = _uiState.value.nearbyDevices.toMutableList()
        val existingIndex = currentDevices.indexOfFirst { it.address == device.address }
        
        val smoothedDevice = if (existingIndex != -1) {
            // อุปกรณ์เคยมีแล้ว - ใช้ Moving Average Filter
            val existingDevice = currentDevices[existingIndex]
            val newHistory = (existingDevice.rssiHistory + device.rssi).takeLast(5) // เก็บ 5 ค่าล่าสุด
            val smoothedRssi = newHistory.average().toInt()
            
            device.copy(
                rssiHistory = newHistory,
                smoothedRssi = smoothedRssi
            )
        } else {
            // อุปกรณ์ใหม่
            device.copy(
                rssiHistory = listOf(device.rssi),
                smoothedRssi = device.rssi
            )
        }
        
        if (existingIndex != -1) {
            currentDevices[existingIndex] = smoothedDevice
        } else {
            currentDevices.add(smoothedDevice)
        }
        
        // เรียงตาม smoothedRssi (สัญญาณแรงสุดก่อน)
        currentDevices.sortByDescending { it.smoothedRssi }
        
        _uiState.value = _uiState.value.copy(nearbyDevices = currentDevices)
    }
    
    fun updateRssiThreshold(threshold: Int) {
        _uiState.value = _uiState.value.copy(rssiThreshold = threshold)
    }
    
    fun setAlarmPlaying(isPlaying: Boolean) {
        _uiState.value = _uiState.value.copy(isAlarmPlaying = isPlaying)
    }
    
    fun clearDevices() {
        _uiState.value = _uiState.value.copy(
            nearbyDevices = emptyList(),
            targetDevice = null
        )
    }
    
    fun switchMode(mode: AppMode) {
        _uiState.value = _uiState.value.copy(currentMode = mode)
    }
    
    fun setGattConnected(isConnected: Boolean) {
        _uiState.value = _uiState.value.copy(isGattConnected = isConnected)
    }
}

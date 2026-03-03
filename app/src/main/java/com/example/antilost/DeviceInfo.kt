package com.example.antilost

data class DeviceInfo(
    val name: String,
    val address: String,
    val rssi: Int,
    val rssiHistory: List<Int> = emptyList(), // เพิ่มประวัติ RSSI
    val smoothedRssi: Int = rssi, // RSSI ที่ผ่านการกรองแล้ว
    val lastSeen: Long = System.currentTimeMillis()
)

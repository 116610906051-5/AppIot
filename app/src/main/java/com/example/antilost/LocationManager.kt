package com.example.antilost

import android.Manifest
import android.content.Context
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase


data class LocationLog(
    val deviceId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val status: String = "",  // "Connected" or "Disconnected"
    val timestamp: Long = 0L,
    val deviceName: String = "",
    val accuracy: Float = 0f
)

class LocationManager(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    private val firestore = Firebase.firestore
    private var locationCallback: LocationCallback? = null

    /**
     * ดึงพิกัด GPS ปัจจุบันและบันทึกลง Firestore
     */
    fun logLocationEvent(
        deviceId: String,
        deviceName: String,
        status: String,  // "Connected" or "Disconnected"
        onComplete: (Boolean) -> Unit = {}
    ) {
        // ตรวจสอบสิทธิ์
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            onComplete(false)
            return
        }

        // สร้าง Location Request
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000  // Update interval 1 second
        ).build()

        // ดึงพิกัดล่าสุด
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    // บันทึกสถานที่ลง Firestore
                    val locationLog = hashMapOf(
                        "deviceId" to deviceId,
                        "deviceName" to deviceName,
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "accuracy" to location.accuracy,
                        "status" to status,
                        "timestamp" to FieldValue.serverTimestamp()
                    )

                    firestore.collection("LocationLogs")
                        .add(locationLog)
                        .addOnSuccessListener {
                            onComplete(true)
                            fusedLocationClient.removeLocationUpdates(this)
                        }
                        .addOnFailureListener {
                            onComplete(false)
                            fusedLocationClient.removeLocationUpdates(this)
                        }
                    return
                }
            }
        }

        // เริ่มขออัปเดตพิกัด
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        // Timeout หลัง 15 วินาที ถ้ายังไม่ได้พิกัด
        android.os.Handler(Looper.getMainLooper()).postDelayed({
            onComplete(false)
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }, 15000)
    }

    /**
     * เรียกหา Location Logs ทั้งหมดจาก Firestore แบบ Real-time
     */
    fun fetchLocationLogs(onResult: (List<LocationLog>) -> Unit) {
        firestore.collection("LocationLogs")
            .limit(100)
            .addSnapshotListener { querySnapshot, error ->
                if (error != null) {
                    android.util.Log.e("LocationManager", "Error fetching logs", error)
                    onResult(emptyList())
                    return@addSnapshotListener
                }

                if (querySnapshot != null) {
                    try {
                        val logs = querySnapshot.documents.mapNotNull { doc ->
                            try {
                                // ดึง timestamp จาก Firestore Timestamp object
                                val timestamp = doc.getTimestamp("timestamp")?.toDate()?.time ?: System.currentTimeMillis()
                                
                                LocationLog(
                                    deviceId = doc.getString("deviceId") ?: "",
                                    deviceName = doc.getString("deviceName") ?: "",
                                    latitude = doc.getDouble("latitude") ?: 0.0,
                                    longitude = doc.getDouble("longitude") ?: 0.0,
                                    accuracy = (doc.getDouble("accuracy") ?: 0.0).toFloat(),
                                    status = doc.getString("status") ?: "",
                                    timestamp = timestamp
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("LocationManager", "Error parsing log: ${e.message}", e)
                                null
                            }
                        }
                        // Sort by timestamp descending
                        val sortedLogs = logs.sortedByDescending { it.timestamp }
                        android.util.Log.d("LocationManager", "Fetched ${sortedLogs.size} logs")
                        onResult(sortedLogs)
                    } catch (e: Exception) {
                        android.util.Log.e("LocationManager", "Error processing logs", e)
                        onResult(emptyList())
                    }
                } else {
                    onResult(emptyList())
                }
            }
    }

    /**
     * ลบข้อมูล Location Logs ทั้งหมด
     */
    fun clearAllLogs() {
        firestore.collection("LocationLogs")
            .get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    firestore.collection("LocationLogs").document(doc.id).delete()
                }
            }
    }
}

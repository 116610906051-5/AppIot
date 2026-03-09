package com.example.antilost

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.example.antilost.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

// ViewModel สำหรับจัดการ Location Logs
class LocationHistoryViewModel(private val locationManager: LocationManager) : ViewModel() {
    private val _locationLogs = MutableStateFlow<List<LocationLog>>(emptyList())
    val locationLogs: StateFlow<List<LocationLog>> = _locationLogs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        fetchLocationLogs()
    }

    fun fetchLocationLogs() {
        _isLoading.value = true
        locationManager.fetchLocationLogs { logs ->
            _locationLogs.value = logs
            _isLoading.value = false
        }
    }

    fun clearAllLogs() {
        locationManager.clearAllLogs()
        _locationLogs.value = emptyList()
    }
}

// Composable สำหรับแสดง Location History
@Composable
fun LocationHistoryScreen(viewModel: LocationHistoryViewModel) {
    val locationLogs by viewModel.locationLogs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF07101F),
                        0.45f to Color(0xFF0B1A35),
                        1.0f to Color(0xFF050D1A)
                    )
                )
            )
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF0B1A35), Color(0xFF091528))
                    )
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(listOf(Primary, Secondary))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            "Location History",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                brush = Brush.linearGradient(listOf(Color.White, Color(0xFFB0C8F0)))
                            )
                        )
                        Text(
                            "${locationLogs.size} records",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF7A94B8)
                        )
                    }
                }
                IconButton(
                    onClick = { viewModel.fetchLocationLogs() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFF8BAFD6),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
            }
        } else if (locationLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF3A5070),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "ไม่มีข้อมูล Location Log",
                        color = Color(0xFF5A7A9E),
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 28.dp, top = 16.dp)
            ) {
                item {
                    // Clear all button
                    Button(
                        onClick = { viewModel.clearAllLogs() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Error.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("ลบทั้งหมด", color = Error, fontWeight = FontWeight.SemiBold)
                    }
                }
                items(locationLogs) { log ->
                    LocationLogCard(log)
                }
            }
        }
    }
}

@Composable
fun LocationLogCard(log: LocationLog) {
    val statusColor = when (log.status) {
        "Connected" -> Success
        "Alarm" -> Color(0xFFFFB84D)
        else -> Error
    }
    val statusIcon = when (log.status) {
        "Connected" -> Icons.Default.Check
        "Alarm" -> Icons.Default.Warning
        else -> Icons.Default.Close
    }
    val context = LocalContext.current
    val formattedTime = formatTimestamp(log.timestamp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        statusColor.copy(alpha = 0.08f),
                        Color(0xFF0D1524)
                    )
                )
            )
            .border(1.dp, statusColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header row: Status + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            log.status,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            fontSize = 14.sp
                        )
                        Text(
                            formattedTime,
                            fontSize = 11.sp,
                            color = Color(0xFF5A7A9E)
                        )
                    }
                }
            }

            // Device info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = Secondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    log.deviceName,
                    fontSize = 13.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Location row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                Text(
                    "%.6f, %.6f".format(log.latitude, log.longitude),
                    fontSize = 12.sp,
                    color = Color(0xFF7A94B8),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = {
                        val uri = android.net.Uri.parse("geo:${log.latitude},${log.longitude}?q=${log.latitude},${log.longitude}")
                        val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        try {
                            context.startActivity(mapIntent)
                        } catch (_: Exception) {
                            val webIntent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://maps.google.com/?q=${log.latitude},${log.longitude}")
                            )
                            context.startActivity(webIntent)
                        }
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "Open map",
                        tint = Primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Accuracy badge
            Box(
                modifier = Modifier
                    .align(Alignment.Start)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E2E45))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "Accuracy: ±${log.accuracy.toInt()}m",
                    fontSize = 10.sp,
                    color = Color(0xFF5A7A9E)
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return if (timestamp > 0) {
        val date = Date(timestamp)
        val formatter = SimpleDateFormat("MMM dd, HH:mm:ss", Locale("th", "TH"))
        formatter.format(date)
    } else {
        "Unknown"
    }
}

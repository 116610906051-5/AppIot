package com.example.antilost

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntiLostScreen(
    uiState: UiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onThresholdChange: (Int) -> Unit,
    onFindDevice: () -> Unit,
    onSwitchMode: (AppMode) -> Unit,
    onConnectDevice: () -> Unit,
    onDisconnectDevice: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "AntiLost App",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Default.Settings, "การตั้งค่า")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Bluetooth Status Card
            BluetoothStatusCard(isEnabled = uiState.isBluetoothEnabled)
            
            // Mode Selector (Tab-like)
            ModeSelectorCard(
                currentMode = uiState.currentMode,
                onModeSelected = onSwitchMode
            )
            
            // แสดงเนื้อหาตามโหมด
            when (uiState.currentMode) {
                AppMode.ALARM_MODE -> {
                    AlarmModeContent(
                        uiState = uiState,
                        onStartScan = onStartScan,
                        onStopScan = onStopScan,
                        onThresholdChange = onThresholdChange,
                        showSettings = showSettings
                    )
                }
                AppMode.FIND_MODE -> {
                    FindModeContent(
                        uiState = uiState,
                        onStartScan = onStartScan,
                        onStopScan = onStopScan,
                        onConnectDevice = onConnectDevice,
                        onDisconnectDevice = onDisconnectDevice,
                        onFindDevice = onFindDevice,
                        onThresholdChange = onThresholdChange,
                        showSettings = showSettings
                    )
                }
            }
        }
    }
}

@Composable
fun BluetoothStatusCard(isEnabled: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) 
                MaterialTheme.colorScheme.primaryContainer
            else 
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isEnabled) Icons.Default.Info else Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isEnabled) 
                    MaterialTheme.colorScheme.onPrimaryContainer
                else 
                    MaterialTheme.colorScheme.onErrorContainer
            )
            Column {
                Text(
                    text = "Bluetooth",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isEnabled) "เปิดใช้งาน" else "ปิดใช้งาน",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun ScanControlCard(
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Animated scanning indicator
            if (isScanning) {
                val infiniteTransition = rememberInfiniteTransition(label = "scan")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )
                
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }
            }
            
            Button(
                onClick = if (isScanning) onStopScan else onStartScan,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) 
                        MaterialTheme.colorScheme.error
                    else 
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isScanning) "หยุดสแกน" else "เริ่มสแกน",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun SettingsCard(
    threshold: Int,
    onThresholdChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "การตั้งค่า",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                "ระดับสัญญาณแจ้งเตือน: $threshold dBm",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Slider(
                value = threshold.toFloat(),
                onValueChange = { onThresholdChange(it.toInt()) },
                valueRange = -100f..-50f,
                steps = 49
            )
            
            Text(
                "ค่ายิ่งต่ำ = แจ้งเตือนเมื่ออยู่ไกลมากขึ้น",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TargetDeviceCard(
    device: DeviceInfo,
    threshold: Int,
    isAlarmPlaying: Boolean
) {
    val isInRange = device.smoothedRssi >= threshold
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAlarmPlaying) 
                MaterialTheme.colorScheme.errorContainer
            else if (isInRange)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (isAlarmPlaying) 
                        MaterialTheme.colorScheme.error
                    else 
                        MaterialTheme.colorScheme.primary
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        device.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        device.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            SignalStrengthIndicator(rssi = device.smoothedRssi, threshold = threshold)
            
            if (isAlarmPlaying) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "⚠️ สัญญาณอ่อน! อาจอยู่ไกลเกินไป",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceCard(device: DeviceInfo, threshold: Int) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        device.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        device.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    "${device.smoothedRssi} dBm",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = getSignalColor(device.smoothedRssi, threshold)
                )
            }
            
            SignalStrengthIndicator(rssi = device.smoothedRssi, threshold = threshold)
        }
    }
}

@Composable
fun SignalStrengthIndicator(rssi: Int, threshold: Int) {
    val signalPercent = ((rssi + 100) / 50f).coerceIn(0f, 1f)
    val isWeak = rssi < threshold
    
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "ความแรงสัญญาณ",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                when {
                    rssi >= -60 -> "แรงมาก"
                    rssi >= -70 -> "แรง"
                    rssi >= threshold -> "ปานกลาง"
                    else -> "อ่อน"
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = getSignalColor(rssi, threshold)
            )
        }
        
        LinearProgressIndicator(
            progress = { signalPercent },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = getSignalColor(rssi, threshold),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
fun getSignalColor(rssi: Int, threshold: Int): Color {
    return when {
        rssi >= -60 -> Color(0xFF4CAF50) // เขียว
        rssi >= -70 -> Color(0xFF8BC34A) // เขียวอ่อน
        rssi >= threshold -> Color(0xFFFFC107) // เหลือง
        else -> Color(0xFFF44336) // แดง
    }
}

// ========== Mode Selector Card ==========
@Composable
fun ModeSelectorCard(
    currentMode: AppMode,
    onModeSelected: (AppMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Alarm Mode Button
            FilterChip(
                selected = currentMode == AppMode.ALARM_MODE,
                onClick = { onModeSelected(AppMode.ALARM_MODE) },
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("แจ้งเตือนของหาย")
                    }
                },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
            
            // Find Mode Button
            FilterChip(
                selected = currentMode == AppMode.FIND_MODE,
                onClick = { onModeSelected(AppMode.FIND_MODE) },
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("หาของ")
                    }
                },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiary
                )
            )
        }
    }
}

// ========== Alarm Mode Content ==========
@Composable
fun AlarmModeContent(
    uiState: UiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onThresholdChange: (Int) -> Unit,
    showSettings: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Scan Control Card
        ScanControlCard(
            isScanning = uiState.isScanning,
            onStartScan = onStartScan,
            onStopScan = onStopScan
        )
        
        // Settings Panel
        AnimatedVisibility(visible = showSettings) {
            SettingsCard(
                threshold = uiState.rssiThreshold,
                onThresholdChange = onThresholdChange
            )
        }
        
        // Target Device Card
        if (uiState.targetDevice != null) {
            TargetDeviceCard(
                device = uiState.targetDevice,
                threshold = uiState.rssiThreshold,
                isAlarmPlaying = uiState.isAlarmPlaying
            )
        }
        
        // Nearby Devices List
        if (uiState.nearbyDevices.isNotEmpty()) {
            Text(
                "อุปกรณ์ใกล้เคียง (${uiState.nearbyDevices.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.nearbyDevices) { device ->
                    DeviceCard(device = device, threshold = uiState.rssiThreshold)
                }
            }
        } else if (uiState.isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        "กำลังสแกนหาอุปกรณ์...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ========== Find Mode Content ==========
@Composable
fun FindModeContent(
    uiState: UiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: () -> Unit,
    onDisconnectDevice: () -> Unit,
    onFindDevice: () -> Unit,
    onThresholdChange: (Int) -> Unit,
    showSettings: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Settings Panel
        AnimatedVisibility(visible = showSettings) {
            SettingsCard(
                threshold = uiState.rssiThreshold,
                onThresholdChange = onThresholdChange
            )
        }
        
        // การ์ดสแกนหาอุปกรณ์ (สำหรับโหมดหาของ)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "สแกนหาอุปกรณ์ ESP32",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Text(
                    if (uiState.targetDevice != null) 
                        "✅ พบ ESP32_AntiLost แล้ว" 
                    else if (uiState.isScanning)
                        "🔍 กำลังสแกน..."
                    else
                        "⚠️ ยังไม่พบอุปกรณ์ ESP32_AntiLost",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.targetDevice != null) 
                        Color(0xFF4CAF50) 
                    else 
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
                
                if (uiState.targetDevice != null) {
                    Text(
                        "ที่อยู่: ${uiState.targetDevice.address}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // ปุ่มสแกน/หยุดสแกน
                Button(
                    onClick = if (uiState.isScanning) onStopScan else onStartScan,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isScanning) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = if (uiState.isScanning) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (uiState.isScanning) "หยุดสแกน" else "สแกนหาอุปกรณ์",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
        
        // Connection Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.isGattConnected) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isGattConnected) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (uiState.isGattConnected) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                    Text(
                        if (uiState.isGattConnected) "เชื่อมต่อแล้ว" else "ยังไม่ได้เชื่อมต่อ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                if (uiState.targetDevice != null) {
                    Text(
                        "อุปกรณ์: ${uiState.targetDevice.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "ที่อยู่: ${uiState.targetDevice.address}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "ไม่พบอุปกรณ์เป้าหมาย (ESP32_AntiLost)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                // Connect/Disconnect Button
                Button(
                    onClick = if (uiState.isGattConnected) onDisconnectDevice else onConnectDevice,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.targetDevice != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isGattConnected) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (uiState.isGattConnected) Icons.Default.Close else Icons.Default.Check,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (uiState.isGattConnected) "ตัดการเชื่อมต่อ" else "เชื่อมต่อกับอุปกรณ์",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
        
        // Beep Button (enabled only when connected)
        Button(
            onClick = onFindDevice,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.isGattConnected,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "🔊 ส่งสัญญาณ Beep",
                style = MaterialTheme.typography.titleLarge
            )
        }
        
        // Instructions Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        "วิธีใช้งาน",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "1. กดปุ่ม 'เชื่อมต่อกับอุปกรณ์' เพื่อเชื่อมต่อกับ ESP32",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "2. เมื่อเชื่อมต่อสำเร็จ กดปุ่ม 'ส่งสัญญาณ Beep' เพื่อทำให้อุปกรณ์ส่งเสียง",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "3. ในโหมดนี้ระบบจะไม่แจ้งเตือนอัตโนมัติ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

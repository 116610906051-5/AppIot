package com.example.antilost

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antilost.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ──────────────────────────────────────────────────────────────────────────────
// Helper: dark glass card modifier
// ──────────────────────────────────────────────────────────────────────────────
private fun Modifier.glassCard(
    cornerRadius: Dp = 20.dp,
    borderAlpha: Float = 0.18f
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(Color(0x1AFFFFFF))
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = borderAlpha),
                Color.White.copy(alpha = 0.04f)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )

// ──────────────────────────────────────────────────────────────────────────────
// Root Screen
// ──────────────────────────────────────────────────────────────────────────────
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

    Box(
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
        // Decorative glow blobs
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Primary.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.12f),
                    radius = size.width * 0.55f
                ),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.8f, size.height * 0.12f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Secondary.copy(alpha = 0.09f), Color.Transparent),
                    center = Offset(size.width * 0.1f, size.height * 0.75f),
                    radius = size.width * 0.5f
                ),
                radius = size.width * 0.5f,
                center = Offset(size.width * 0.1f, size.height * 0.75f)
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            AppTopBar(
                isBluetoothEnabled = uiState.isBluetoothEnabled,
                showSettings = showSettings,
                onToggleSettings = { showSettings = !showSettings }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp, top = 20.dp)
            ) {
                // Mode Selector
                item { ModeSelectorRow(currentMode = uiState.currentMode, onModeSelected = onSwitchMode) }

                // Settings panel
                item {
                    AnimatedVisibility(
                        visible = showSettings,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        SettingsCard(
                            threshold = uiState.rssiThreshold,
                            onThresholdChange = onThresholdChange
                        )
                    }
                }

                // Mode-specific content
                when (uiState.currentMode) {
                    AppMode.ALARM_MODE -> {
                        item {
                            AlarmScanCard(
                                isScanning = uiState.isScanning,
                                onStartScan = onStartScan,
                                onStopScan = onStopScan
                            )
                        }
                        if (uiState.targetDevice != null) {
                            item {
                                TargetDeviceCard(
                                    device = uiState.targetDevice,
                                    threshold = uiState.rssiThreshold,
                                    isAlarmPlaying = uiState.isAlarmPlaying
                                )
                            }
                        }
                        if (uiState.nearbyDevices.isNotEmpty()) {
                            item {
                                Text(
                                    "อุปกรณ์ใกล้เคียง  •  ${uiState.nearbyDevices.size} รายการ",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFF7A94B8),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                            items(uiState.nearbyDevices) { device ->
                                DeviceCard(device = device, threshold = uiState.rssiThreshold)
                            }
                        } else if (uiState.isScanning) {
                            item { ScanningEmptyState() }
                        }
                    }
                    AppMode.FIND_MODE -> {
                        item {
                            FindScanCard(
                                uiState = uiState,
                                onStartScan = onStartScan,
                                onStopScan = onStopScan
                            )
                        }
                        item {
                            ConnectionCard(
                                uiState = uiState,
                                onConnect = onConnectDevice,
                                onDisconnect = onDisconnectDevice
                            )
                        }
                        item { BeepButton(isConnected = uiState.isGattConnected, onFindDevice = onFindDevice) }
                        item { HowToUseCard() }
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Top App Bar
// ──────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    isBluetoothEnabled: Boolean,
    showSettings: Boolean,
    onToggleSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF0B1A35), Color(0xFF091528))
                )
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon circle
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(Primary, Secondary))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "AntiLost",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        brush = Brush.linearGradient(listOf(Color.White, Color(0xFFB0C8F0)))
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isBluetoothEnabled) Success else Error)
                    )
                    Text(
                        text = if (isBluetoothEnabled) "Bluetooth พร้อมใช้งาน" else "Bluetooth ปิดอยู่",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isBluetoothEnabled) Success else Error
                    )
                }
            }
            IconButton(
                onClick = onToggleSettings,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (showSettings)
                            Primary.copy(alpha = 0.25f)
                        else
                            Color.White.copy(alpha = 0.08f)
                    )
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "การตั้งค่า",
                    tint = if (showSettings) Primary else Color(0xFF8BAFD6),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Mode Selector Row
// ──────────────────────────────────────────────────────────────────────────────
@Composable
fun ModeSelectorRow(
    currentMode: AppMode,
    onModeSelected: (AppMode) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0E1829))
            .border(1.dp, Color(0xFF1E2E45), RoundedCornerShape(16.dp))
            .padding(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ModeTab(
                label = "แจ้งเตือน",
                icon = Icons.Default.Warning,
                selected = currentMode == AppMode.ALARM_MODE,
                selectedColor = Error,
                modifier = Modifier.weight(1f),
                onClick = { onModeSelected(AppMode.ALARM_MODE) }
            )
            ModeTab(
                label = "หาของ",
                icon = Icons.Default.Search,
                selected = currentMode == AppMode.FIND_MODE,
                selectedColor = Secondary,
                modifier = Modifier.weight(1f),
                onClick = { onModeSelected(AppMode.FIND_MODE) }
            )
        }
    }
}

@Composable
fun ModeTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    selectedColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        if (selected) selectedColor.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = tween(300), label = "tab_bg"
    )
    val contentColor by animateColorAsState(
        if (selected) selectedColor else Color(0xFF5A7A9E),
        animationSpec = tween(300), label = "tab_fg"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(
                if (selected) Modifier.border(1.dp, selectedColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                else Modifier
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                color = contentColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 13.sp
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Settings Card
// ──────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsCard(threshold: Int, onThresholdChange: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard()
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Settings, null, tint = Primary, modifier = Modifier.size(20.dp))
                Text("ตั้งค่าระยะสัญญาณ", fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ขีดจำกัด", color = Color(0xFF7A94B8), fontSize = 13.sp)
                Text(
                    "$threshold dBm",
                    color = Primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Slider(
                value = threshold.toFloat(),
                onValueChange = { onThresholdChange(it.toInt()) },
                valueRange = -100f..-50f,
                steps = 49,
                colors = SliderDefaults.colors(
                    thumbColor = Primary,
                    activeTrackColor = Primary,
                    inactiveTrackColor = Color(0xFF1E2E45)
                )
            )
            Text(
                "ค่ายิ่งต่ำ → แจ้งเตือนเมื่ออยู่ไกลมากขึ้น",
                fontSize = 11.sp,
                color = Color(0xFF4A6280)
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Alarm Mode – Scan Card with Radar Animation
// ──────────────────────────────────────────────────────────────────────────────
@Composable
fun AlarmScanCard(
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 24.dp)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (isScanning) {
                RadarAnimation()
            } else {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(listOf(Primary.copy(alpha = 0.2f), Color.Transparent))
                        )
                        .border(1.dp, Primary.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                            Icons.Default.Info,
                        contentDescription = null,
                        tint = Primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Text(
                if (isScanning) "กำลังสแกน..." else "พร้อมสแกน",
                color = if (isScanning) Primary else Color(0xFF7A94B8),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )

            GradientButton(
                text = if (isScanning) "หยุดสแกน" else "เริ่มสแกน",
                icon = if (isScanning) Icons.Default.Close else Icons.Default.PlayArrow,
                gradient = if (isScanning)
                    Brush.horizontalGradient(listOf(Error, Color(0xFFFF8099)))
                else
                    Brush.horizontalGradient(listOf(Primary, Secondary)),
                onClick = if (isScanning) onStopScan else onStartScan
            )
        }
    }
}

// Radar Animation
@Composable
fun RadarAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "angle"
    )
    val ring1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "ring1"
    )
    val ring2 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "ring2"
    )

    Canvas(modifier = Modifier.size(96.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val outerR = size.minDimension / 2
        // Rings
        drawCircle(Primary.copy(alpha = 0.15f * ring1), outerR, center)
        drawCircle(Primary.copy(alpha = 0.1f * ring2), outerR * 0.65f, center)
        drawCircle(Primary.copy(alpha = 0.06f), outerR * 0.35f, center)
        // Border
        drawCircle(Primary.copy(alpha = 0.4f), outerR, center, style = Stroke(1.5f))

        // Sweep
        val sweepBrush = Brush.sweepGradient(
            listOf(Color.Transparent, Primary.copy(alpha = 0.0f), Primary.copy(alpha = 0.5f)),
            center = center
        )
        drawArc(
            brush = sweepBrush,
            startAngle = angle - 90f,
            sweepAngle = 120f,
            useCenter = true,
            topLeft = Offset(center.x - outerR, center.y - outerR),
            size = Size(outerR * 2, outerR * 2)
        )
        // Scanner dot
        val dotX = center.x + (outerR - 4.dp.toPx()) * cos(Math.toRadians(angle.toDouble() - 90).toFloat())
        val dotY = center.y + (outerR - 4.dp.toPx()) * sin(Math.toRadians(angle.toDouble() - 90).toFloat())
        drawCircle(Color.White, 4.dp.toPx(), Offset(dotX, dotY))
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Target Device Card
// ──────────────────────────────────────────────────────────────────────────────
@Composable
fun TargetDeviceCard(device: DeviceInfo, threshold: Int, isAlarmPlaying: Boolean) {
    val isInRange = device.smoothedRssi >= threshold
    val accentColor = when {
        isAlarmPlaying -> Error
        isInRange -> Success
        else -> Warning
    }
    val alarmAnim = rememberInfiniteTransition(label = "alarm")
    val alarmAlpha by alarmAnim.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "alarmA"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        accentColor.copy(alpha = 0.12f),
                        Color(0xFF0D1524)
                    )
                )
            )
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = if (isAlarmPlaying) 0.3f * alarmAlpha else 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    Text(device.address, fontSize = 11.sp, color = Color(0xFF5A7A9E))
                }
                // Status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.2f))
                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        when {
                            isAlarmPlaying -> "แจ้งเตือน!"
                            isInRange -> "ในระยะ"
                            else -> "ไกลเกิน"
                        },
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            CircularSignalMeter(rssi = device.smoothedRssi, threshold = threshold)

            if (isAlarmPlaying) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Error.copy(alpha = 0.1f * alarmAlpha))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, null, tint = Error, modifier = Modifier.size(18.dp))
                    Text(
                        "สัญญาณอ่อน! อุปกรณ์อาจอยู่ไกลเกินไป",
                        color = Error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Circular Signal Meter
// ──────────────────────────────────────────────────────────────────────────────
@Composable
fun CircularSignalMeter(rssi: Int, threshold: Int) {
    val signalPct = ((rssi + 100) / 50f).coerceIn(0f, 1f)
    val arcColor = signalColor(rssi, threshold)
    val animPct by animateFloatAsState(
        targetValue = signalPct,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "signal"
    )
    val label = when {
        rssi >= -60 -> "แรงมาก"
        rssi >= -70 -> "แรง"
        rssi >= threshold -> "ปานกลาง"
        else -> "อ่อน"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Canvas(modifier = Modifier.size(72.dp)) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2
            val arcRect = Size(size.width - stroke, size.height - stroke)
            val arcOffset = Offset(inset, inset)

            // Track
            drawArc(Color(0xFF1E2E45), 150f, 240f, false, arcOffset, arcRect, style = Stroke(stroke, cap = StrokeCap.Round))
            // Value
            drawArc(
                Brush.sweepGradient(listOf(arcColor.copy(alpha = 0.6f), arcColor), center = Offset(size.width / 2, size.height / 2)),
                150f, 240f * animPct, false, arcOffset, arcRect,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            // End dot
            val endAngleRad = Math.toRadians((150.0 + 240.0 * animPct))
            val r = (size.minDimension - stroke) / 2
            val cx = size.width / 2 + r * cos(endAngleRad).toFloat()
            val cy = size.height / 2 + r * sin(endAngleRad).toFloat()
            drawCircle(arcColor, stroke / 2 + 1.dp.toPx(), Offset(cx, cy))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("ความแรงสัญญาณ", fontSize = 12.sp, color = Color(0xFF5A7A9E))
            Text("$rssi dBm", fontWeight = FontWeight.Bold, color = arcColor, fontSize = 18.sp)
            Text(label, fontSize = 11.sp, color = arcColor.copy(alpha = 0.8f))
        }
    }
}

fun signalColor(rssi: Int, threshold: Int) = when {
    rssi >= -60 -> Success
    rssi >= -70 -> Color(0xFF6EE896)
    rssi >= threshold -> Warning
    else -> Error
}

// ──────────────────────────────────────────────────────────────────────────────
// Nearby Device Card
// ──────────────────────────────────────────────────────────────────────────────
@Composable
fun DeviceCard(device: DeviceInfo, threshold: Int) {
    val sc = signalColor(device.smoothedRssi, threshold)
    val signalPct = ((device.smoothedRssi + 100) / 50f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(16.dp)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Signal bars icon
            Column(
                modifier = Modifier.width(28.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (i in 3 downTo 1) {
                    val filled = signalPct * 3 >= i
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((5 + i * 4).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (filled) sc else Color(0xFF1E2E45))
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
                Text(device.address, fontSize = 10.sp, color = Color(0xFF4A6280))
            }
            Text(
                "${device.smoothedRssi} dBm",
                color = sc,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Scanning Empty State
// ──────────────────────────────────────────────────────────────────────────────
@Composable
fun ScanningEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
            Text("กำลังสแกนหาอุปกรณ์...", color = Color(0xFF5A7A9E), fontSize = 14.sp)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Find Mode – Scan Card
// ──────────────────────────────────────────────────────────────────────────────
@Composable
fun FindScanCard(uiState: UiState, onStartScan: () -> Unit, onStopScan: () -> Unit) {
    val statusColor = if (uiState.targetDevice != null) Success else if (uiState.isScanning) Primary else Color(0xFF5A7A9E)
    val statusText = when {
        uiState.targetDevice != null -> "พบ ESP32_AntiLost แล้ว"
        uiState.isScanning -> "กำลังสแกน..."
        else -> "ยังไม่พบอุปกรณ์ ESP32_AntiLost"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard()
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Search, null, tint = Secondary, modifier = Modifier.size(22.dp))
                Text("สแกนหาอุปกรณ์ ESP32", fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor)
                )
                Text(statusText, fontSize = 13.sp, color = statusColor)
            }
            if (uiState.targetDevice != null) {
                Text(
                    "ที่อยู่: ${uiState.targetDevice.address}",
                    fontSize = 11.sp,
                    color = Color(0xFF4A6280)
                )
            }
            GradientButton(
                text = if (uiState.isScanning) "หยุดสแกน" else "สแกนหาอุปกรณ์",
                icon = if (uiState.isScanning) Icons.Default.Close else Icons.Default.Search,
                gradient = if (uiState.isScanning)
                    Brush.horizontalGradient(listOf(Error, Color(0xFFFF7090)))
                else
                    Brush.horizontalGradient(listOf(Color(0xFF006EAA), Secondary)),
                onClick = if (uiState.isScanning) onStopScan else onStartScan
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Connection Card
// ──────────────────────────────────────────────────────────────────────────────
@Composable
fun ConnectionCard(uiState: UiState, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val isConnected = uiState.isGattConnected
    val connColor = if (isConnected) Success else Color(0xFF5A7A9E)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isConnected)
                    Brush.linearGradient(listOf(Success.copy(alpha = 0.1f), Color(0xFF0D1524)))
                else
                    Brush.linearGradient(listOf(Color(0xFF1A2233), Color(0xFF0D1524)))
            )
            .border(
                1.dp,
                if (isConnected) Success.copy(alpha = 0.4f) else Color(0xFF1E2E45),
                RoundedCornerShape(20.dp)
            )
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val pulse = rememberInfiniteTransition(label = "conn")
                val pulseAlpha by pulse.animateFloat(
                    initialValue = 0.5f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                    label = "pA"
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(connColor.copy(alpha = if (isConnected) pulseAlpha else 1f))
                )
                Text(
                    if (isConnected) "เชื่อมต่อแล้ว" else "ยังไม่ได้เชื่อมต่อ",
                    fontWeight = FontWeight.SemiBold,
                    color = connColor,
                    fontSize = 16.sp
                )
            }
            if (uiState.targetDevice != null) {
                Text("อุปกรณ์: ${uiState.targetDevice.name}", fontSize = 13.sp, color = Color(0xFF7A94B8))
                Text("ที่อยู่: ${uiState.targetDevice.address}", fontSize = 11.sp, color = Color(0xFF4A6280))
            } else {
                Text("ไม่พบ ESP32_AntiLost — กรุณาสแกนก่อน", fontSize = 13.sp, color = Error.copy(alpha = 0.8f))
            }
            GradientButton(
                text = if (isConnected) "ตัดการเชื่อมต่อ" else "เชื่อมต่อกับอุปกรณ์",
                icon = if (isConnected) Icons.Default.Close else Icons.Default.Check,
                gradient = if (isConnected)
                    Brush.horizontalGradient(listOf(Error, ErrorLight.copy(alpha = 0.8f)))
                else
                    Brush.horizontalGradient(listOf(Primary, AccentPurple)),
                enabled = uiState.targetDevice != null,
                onClick = if (isConnected) onDisconnect else onConnect
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Beep Button
// ──────────────────────────────────────────────────────────────────────────────
@Composable
fun BeepButton(isConnected: Boolean, onFindDevice: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "beep")
    val haloScale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "haloS"
    )
    val haloAlpha by pulse.animateFloat(
        initialValue = 0.35f, targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "haloA"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isConnected) {
            // Halo effect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .graphicsLayer(scaleX = haloScale, scaleY = haloScale, alpha = haloAlpha)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Secondary.copy(alpha = 0.25f))
            )
        }
        Button(
            onClick = onFindDevice,
            enabled = isConnected,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color(0xFF1A2233)
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isConnected)
                            Brush.horizontalGradient(listOf(Color(0xFF00B090), Secondary, Primary))
                        else
                            Brush.horizontalGradient(listOf(Color(0xFF1A2233), Color(0xFF1A2233)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = if (isConnected) Color.White else Color(0xFF3A5070),
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        "ส่งสัญญาณ Beep",
                        color = if (isConnected) Color.White else Color(0xFF3A5070),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// How-To-Use Card
// ──────────────────────────────────────────────────────────────────────────────
@Composable
fun HowToUseCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AccentPurple.copy(alpha = 0.07f))
            .border(1.dp, AccentPurple.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, null, tint = AccentPurple, modifier = Modifier.size(18.dp))
                Text("วิธีใช้งาน", fontWeight = FontWeight.SemiBold, color = AccentPurple)
            }
            listOf(
                "1. กดปุ่ม 'สแกนหาอุปกรณ์' เพื่อค้นหา ESP32",
                "2. กด 'เชื่อมต่อกับอุปกรณ์' หลังพบ ESP32",
                "3. กด 'ส่งสัญญาณ Beep' เพื่อให้อุปกรณ์ส่งเสียง",
                "4. โหมดนี้ไม่มีการแจ้งเตือนอัตโนมัติ"
            ).forEach { step ->
                Text(step, fontSize = 12.sp, color = Color(0xFF7A94B8))
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Gradient Button Helper
// ──────────────────────────────────────────────────────────────────────────────
@Composable
fun GradientButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradient: Brush,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color(0xFF1A2233)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (enabled) gradient else Brush.horizontalGradient(listOf(Color(0xFF1A2233), Color(0xFF1A2233)))),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = if (enabled) Color.White else Color(0xFF3A5070), modifier = Modifier.size(20.dp))
                Text(
                    text,
                    color = if (enabled) Color.White else Color(0xFF3A5070),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

// Keep old composables that may still be referenced (unused aliases)
@Composable
fun getSignalColor(rssi: Int, threshold: Int) = signalColor(rssi, threshold)

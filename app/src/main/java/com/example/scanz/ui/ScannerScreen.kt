package com.example.scanz.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scanz.data.Device
import com.example.scanz.scanner.DeviceResolver
import com.example.scanz.scanner.ShizukuHelper
import com.example.scanz.scanner.ShizukuState
import com.example.scanz.scanner.MacVendorResolver
import java.text.SimpleDateFormat
import java.util.*

// --- Modernized Terminal Palette ---
val SurfaceDark = Color(0xFF0D0D0D)
val CardGray = Color(0xFF161616)
val AccentGreen = Color(0xFF00FF41)
val AccentCyan = Color(0xFF00E5FF)
val WarningAmber = Color(0xFFFFB300)
val ErrorRed = Color(0xFFFF3131)
val TextDim = Color(0xFF9E9E9E)
val TextSecondary = Color(0xFFB0B0B0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(viewModel: ScannerViewModel = viewModel()) {
    val devices by viewModel.devices.collectAsState(initial = emptyList())
    val isScanning by viewModel.isScanning.collectAsState()
    val isStealth by viewModel.isStealthMode.collectAsState()
    val auditLog by viewModel.auditLog.collectAsState()
    val idsProb = viewModel.calculateIdsProbability()
    val shizukuState by viewModel.shizukuState.collectAsState()

    var showLogs by remember { mutableStateOf(false) }
    var selectedDeviceForCustom by remember { mutableStateOf<Device?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            viewModel.startScan()
        }
    }

    Scaffold(
        containerColor = SurfaceDark,
        topBar = {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ScanZ // Recon",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isStealth) "STEALTH" else "ACTIVE",
                            color = if (isStealth) AccentGreen else ErrorRed,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isStealth,
                            onCheckedChange = { viewModel.toggleStealthMode(it) },
                            modifier = Modifier.scale(0.7f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AccentGreen,
                                checkedTrackColor = AccentGreen.copy(alpha = 0.2f),
                                uncheckedThumbColor = ErrorRed,
                                uncheckedTrackColor = ErrorRed.copy(alpha = 0.2f)
                            )
                        )
                    }
                }

                // Shizuku Status Line
                Text(
                    text = when (shizukuState) {
                        ShizukuState.ConnectionStatus.CONNECTED -> "● SHIZUKU_LINKED"
                        ShizukuState.ConnectionStatus.NEEDS_AUTH -> "○ AUTH_REQUIRED (TAP)"
                        else -> "× DAEMON_OFFLINE"
                    },
                    color = when (shizukuState) {
                        ShizukuState.ConnectionStatus.CONNECTED -> AccentCyan
                        ShizukuState.ConnectionStatus.NEEDS_AUTH -> WarningAmber
                        else -> TextDim
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier.clickable { viewModel.requestShizukuAccess() }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Main Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModernButton(
                    text = if (isScanning) "STOP" else "START SCAN",
                    color = AccentGreen,
                    modifier = Modifier.weight(1.5f),
                    enabled = true
                ) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE))
                }
                
                ModernButton(
                    text = "LOGS",
                    color = WarningAmber,
                    modifier = Modifier.weight(1f)
                ) { showLogs = true }

                ModernButton(
                    text = "CLEAR",
                    color = TextDim,
                    modifier = Modifier.weight(1f)
                ) { viewModel.clearResults() }
            }

            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(1.dp),
                    color = AccentGreen,
                    trackColor = Color.Transparent
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Device Feed
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(devices, key = { it.ipAddress }) { device ->
                    ModernDeviceCard(device, onLongClick = { selectedDeviceForCustom = it })
                }
            }
        }
    }

    if (showLogs) {
        ModalBottomSheet(
            onDismissRequest = { showLogs = false },
            containerColor = SurfaceDark,
            dragHandle = { BottomSheetDefaults.DragHandle(color = AccentGreen) }
        ) {
            AuditLogUI(auditLog, idsProb)
        }
    }

    if (selectedDeviceForCustom != null) {
        CustomVendorDialog(
            device = selectedDeviceForCustom!!,
            onDismiss = { selectedDeviceForCustom = null },
            onSave = { vendor ->
                viewModel.saveCustomVendor(selectedDeviceForCustom!!.macAddress ?: "", vendor)
                selectedDeviceForCustom = null
            }
        )
    }
}

@Composable
fun ModernButton(text: String, color: Color, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .border(0.5.dp, if (enabled) color.copy(alpha = 0.5f) else TextDim.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
        color = Color.Transparent,
        shape = RoundedCornerShape(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (enabled) color else TextDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernDeviceCard(device: Device, onLongClick: (Device) -> Unit) {
    val isVuln = device.openPorts.any { it == 21 || it == 23 }
    val accentColor = if (isVuln) ErrorRed else AccentGreen
    
    val isPrivate = device.vendorName.contains("Private")
    val isUnknown = device.vendorName == "[Unknown Hardware]"
    
    val vendorDisplay = when {
        isPrivate -> "Mobile Device"
        isUnknown -> "?"
        else -> device.vendorName
    }
    
    val vendorColor = when {
        device.isCustomVendor -> AccentCyan
        isPrivate -> AccentCyan
        isUnknown -> WarningAmber
        else -> Color.White
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = { if (device.macAddress != null) onLongClick(device) }
            ),
        color = CardGray,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Title Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(accentColor, RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = device.ipAddress,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Text(
                    text = device.osFingerprint.replace("[OS: ", "").replace("]", ""),
                    color = WarningAmber,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Vendor & Type Info
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = vendorDisplay,
                    color = vendorColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                
                val subInfo = when {
                    isPrivate -> "(90% Private MAC)"
                    device.deviceName.isNotEmpty() -> device.deviceName
                    else -> ""
                }

                if (subInfo.isNotEmpty()) {
                    Text(text = " • ", color = TextDim)
                    Text(
                        text = subInfo,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }

            // Details Section (Ports / HTTP)
            if (device.openPorts.isNotEmpty() || !device.httpTitle.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (device.openPorts.isNotEmpty()) {
                        DetailTag(text = "PORTS: ${device.openPorts.joinToString(",")}", color = accentColor)
                    }
                    if (!device.httpTitle.isNullOrEmpty()) {
                        DetailTag(text = device.httpTitle, color = AccentCyan)
                    }
                }
            }

            // Technical Footer
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = device.macAddress ?: "MAC_HIDDEN",
                    color = AccentGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
                
                val typeLabel = if (isPrivate) {
                    "PROBABLE_MOBILE"
                } else {
                    device.deviceType.replace("[", "").replace("]", "")
                }

                Text(
                    text = typeLabel,
                    color = TextDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun DetailTag(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.border(0.5.dp, color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
    ) {
        Text(
            text = text,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomVendorDialog(device: Device, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AccentGreen) }
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            Text(text = "MANUAL IDENTIFICATION", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(text = "OUI: ${MacVendorResolver.getOui(device.macAddress)}", color = TextDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            
            Spacer(modifier = Modifier.height(20.dp))
            
            TextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Vendor Name", color = TextDim, fontFamily = FontFamily.Monospace) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = CardGray,
                    unfocusedContainerColor = CardGray,
                    cursorColor = AccentGreen,
                    focusedIndicatorColor = AccentGreen,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Button(
                onClick = { if (text.isNotBlank()) onSave(text) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = SurfaceDark),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "SAVE TO DATABASE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun AuditLogUI(logs: List<ScanEvent>, idsProb: Int) {
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val probColor = when {
        idsProb < 25 -> AccentGreen
        idsProb < 75 -> WarningAmber
        else -> ErrorRed
    }

    Column(modifier = Modifier.fillMaxHeight(0.85f).padding(20.dp)) {
        Text(text = "SESSION_AUDIT_LOG", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Black)
        
        LazyColumn(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
            items(logs.reversed()) { event ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "${sdf.format(Date(event.timestamp))} ",
                        color = TextDim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "| ${event.type.padEnd(8)} | ${event.details}",
                        color = if (event.threatWeight > 0) WarningAmber else TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = CardGray,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(text = "REQUESTS", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(text = "${logs.size}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "IDS PROBABILITY", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(text = "$idsProb%", color = probColor, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

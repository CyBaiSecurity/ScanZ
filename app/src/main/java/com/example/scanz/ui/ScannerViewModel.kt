package com.example.scanz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanz.data.AppDatabase
import com.example.scanz.data.Device
import com.example.scanz.scanner.NetworkScanner
import com.example.scanz.scanner.ShizukuHelper
import com.example.scanz.scanner.ShizukuState
import com.example.scanz.scanner.MacVendorResolver
import rikka.shizuku.Shizuku
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.File

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val deviceDao = db.deviceDao()
    private val scanner = NetworkScanner(application)
    private val customVendorFile = File(application.filesDir, "custom_vendors.json")

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isStealthMode = MutableStateFlow(false)
    val isStealthMode: StateFlow<Boolean> = _isStealthMode.asStateFlow()

    private val _auditLog = MutableStateFlow<List<ScanEvent>>(emptyList())
    val auditLog: StateFlow<List<ScanEvent>> = _auditLog.asStateFlow()

    private val _shizukuError = MutableStateFlow<String?>(null)
    val shizukuError: StateFlow<String?> = _shizukuError.asStateFlow()

    val shizukuState = ShizukuState.status

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        ShizukuState.updateStatus()
    }

    init {
        loadCustomVendors()
        ShizukuHelper.addPermissionListener(permissionListener)
    }

    override fun onCleared() {
        super.onCleared()
        ShizukuHelper.removePermissionListener(permissionListener)
    }

    fun requestShizukuAccess() {
        try {
            if (!ShizukuHelper.isAvailable()) {
                ShizukuState.updateStatus()
                return
            }
            if (ShizukuHelper.checkPermission()) {
                ShizukuState.updateStatus()
            } else {
                ShizukuHelper.requestPermission(0)
            }
        } catch (e: Exception) {
            _shizukuError.value = e.message
        }
    }

    fun toggleStealthMode(enabled: Boolean) {
        _isStealthMode.value = enabled
    }

    fun logEvent(event: ScanEvent) {
        _auditLog.value = _auditLog.value + event
    }

    fun clearLog() {
        _auditLog.value = emptyList()
    }

    fun calculateIdsProbability(): Int {
        val totalWeight = _auditLog.value.sumOf { it.threatWeight }
        return (totalWeight).coerceAtMost(99)
    }

    fun startScan() {
        if (_isScanning.value) return
        
        viewModelScope.launch {
            _isScanning.value = true
            
            scanner.scan(
                stealthMode = _isStealthMode.value,
                onEvent = { logEvent(it) }
            ) { device ->
                viewModelScope.launch {
                    deviceDao.insertDevice(device)
                }
            }
            _isScanning.value = false
        }
    }

    fun clearResults() {
        viewModelScope.launch {
            deviceDao.deleteAll()
            clearLog()
        }
    }

    // --- Custom Vendor Persistence ---
    
    private fun loadCustomVendors() {
        if (customVendorFile.exists()) {
            try {
                val json = customVendorFile.readText()
                val obj = JSONObject(json)
                val map = mutableMapOf<String, String>()
                obj.keys().forEach { key ->
                    map[key] = obj.getString(key)
                }
                MacVendorResolver.updateVendorDictionary(map)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveCustomVendor(mac: String, vendorName: String) {
        val oui = MacVendorResolver.getOui(mac) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentJson = if (customVendorFile.exists()) customVendorFile.readText() else "{}"
                val obj = JSONObject(currentJson)
                obj.put(oui, vendorName)
                customVendorFile.writeText(obj.toString())
                
                // Update active memory
                MacVendorResolver.updateVendorDictionary(mapOf(oui to vendorName))
                
                // Update existing devices in DB with this OUI
                val currentDevices = deviceDao.getAllDevices().first()
                currentDevices.forEach { device ->
                    if (MacVendorResolver.getOui(device.macAddress) == oui) {
                        deviceDao.insertDevice(device.copy(
                            vendorName = vendorName,
                            isCustomVendor = true
                        ))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val devices = deviceDao.getAllDevices()
}

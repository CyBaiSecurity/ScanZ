package com.example.scanz.scanner

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.example.scanz.data.Device
import com.example.scanz.ui.ScanEvent
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Collections

class NetworkScanner(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _foundDevices = Collections.synchronizedSet(mutableSetOf<Device>())
    private val targetPorts = listOf(21, 22, 23, 80, 443, 445, 3306, 8080, 9100)

    suspend fun scan(
        stealthMode: Boolean,
        onEvent: (ScanEvent) -> Unit,
        onDeviceFound: (Device) -> Unit
    ) = coroutineScope {
        _foundDevices.clear()
        val localIp = getLocalIpAddress() ?: return@coroutineScope
        val subnet = localIp.substringBeforeLast(".")

        if (stealthMode) {
            // --- PASSIVE SCANNING STRATEGY ---
            onEvent(ScanEvent(type = "PASSIVE", target = "$subnet.0/24", details = "Reading Shizuku ARP Cache", threatWeight = 0))

            // 1. Resolve from ARP Cache (Zero packets sent)
            val arpTable = ShizukuHelper.getArpTable()
            if (arpTable.isNotEmpty()) {
                arpTable.forEach { (ip, mac) ->
                    val vendor = MacVendorResolver.resolve(mac)
                    // Passive DNS attempt (cached/local resolver only)
                    val (dnsName, dnsType) = DeviceResolver.resolveByDns(ip)
                    
                    val device = Device(
                        ipAddress = ip,
                        macAddress = mac,
                        vendorName = vendor,
                        deviceName = dnsName,
                        deviceType = dnsType,
                        hostname = dnsName,
                        osFingerprint = "[OS: Unknown - Passive Mode]"
                    )
                    updateDeviceList(device, onDeviceFound)
                }
            } else {
                onEvent(ScanEvent(type = "PASSIVE", target = "SYSTEM", details = "ARP Cache Empty or Shizuku Unavailable", threatWeight = 0))
            }

            // 2. Passive mDNS Listener
            startDiscovery(onEvent, { ip -> arpTable[ip] }, onDeviceFound)

            // Stay alive for discovery
            delay(5000)

        } else {
            // --- ACTIVE SCANNING STRATEGY ---
            val arpTable = ShizukuHelper.getArpTable()

            val scanJobs = (1..254).map { i ->
                async(Dispatchers.IO) {
                    val testIp = "$subnet.$i"
                    try {
                        onEvent(ScanEvent(type = "PING", target = testIp, details = "ICMP Echo", threatWeight = 1))
                        val address = InetAddress.getByName(testIp)
                        
                        if (address.isReachable(300)) {
                            onEvent(ScanEvent(type = "DNS", target = testIp, details = "RDNS Lookup", threatWeight = 0))
                            val (dnsName, dnsType) = DeviceResolver.resolveByDns(testIp)

                            val mac = arpTable[testIp]
                            val vendor = MacVendorResolver.resolve(mac)

                            // Active Probing
                            val osTask = async(Dispatchers.IO) {
                                onEvent(ScanEvent(type = "OS_INTENT", target = testIp, details = "TTL Fingerprint", threatWeight = 1))
                                DeviceResolver.fingerprintOs(testIp, hostname = dnsName)
                            }

                            val openPorts = probePorts(testIp, onEvent)
                            val title = if (openPorts.contains(80) || openPorts.contains(8080)) {
                                val port = if (openPorts.contains(80)) 80 else 8080
                                onEvent(ScanEvent(type = "HTTP", target = testIp, details = "Banner Grab", threatWeight = 10))
                                fetchHttpTitle(testIp, port)
                            } else null

                            val device = Device(
                                ipAddress = testIp,
                                macAddress = mac,
                                vendorName = vendor,
                                hostname = address.canonicalHostName,
                                deviceName = dnsName,
                                deviceType = dnsType,
                                openPorts = openPorts,
                                httpTitle = title,
                                osFingerprint = osTask.await()
                            )
                            updateDeviceList(device, onDeviceFound)
                        }
                    } catch (e: Exception) { }
                }
            }
            
            startDiscovery(onEvent, { ip -> arpTable[ip] }, onDeviceFound)
            scanJobs.awaitAll()
            delay(2000)
        }

        stopDiscovery()
    }

    private var activeListeners = mutableListOf<NsdManager.DiscoveryListener>()

    private fun startDiscovery(
        onEvent: (ScanEvent) -> Unit,
        getMac: (String) -> String?,
        onDeviceFound: (Device) -> Unit
    ) {
        val serviceTypes = listOf("_ipp._tcp", "_pdl-datastream._tcp", "_googlecast._tcp", "_airplay._tcp", "_http._tcp")
        serviceTypes.forEach { type ->
            val listener = createDiscoveryListener(type, onEvent, getMac, onDeviceFound)
            activeListeners.add(listener)
            try {
                nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) { }
        }
    }

    private fun stopDiscovery() {
        activeListeners.forEach {
            try { nsdManager.stopServiceDiscovery(it) } catch (e: Exception) { }
        }
        activeListeners.clear()
    }

    private suspend fun probePorts(ip: String, onEvent: (ScanEvent) -> Unit): List<Int> = coroutineScope {
        targetPorts.map { port ->
            async(Dispatchers.IO) {
                try {
                    onEvent(ScanEvent(type = "TCP", target = ip, details = "Port Check: $port", threatWeight = 5))
                    val socket = Socket()
                    socket.connect(InetSocketAddress(ip, port), 500)
                    socket.close()
                    port
                } catch (e: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    private fun fetchHttpTitle(ip: String, port: Int): String? {
        return try {
            val url = URL("http://$ip:$port/")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 1000
            connection.readTimeout = 1000
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val match = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(html)
            match?.groupValues?.get(1)?.trim()
        } catch (e: Exception) {
            null
        }
    }

    private fun updateDeviceList(device: Device, onDeviceFound: (Device) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            var updatedDevice = device
            synchronized(_foundDevices) {
                val existing = _foundDevices.find { it.ipAddress == device.ipAddress }
                if (existing != null) {
                    updatedDevice = existing.copy(
                        deviceName = if (device.deviceName.isNotEmpty()) device.deviceName else existing.deviceName,
                        deviceType = if (device.deviceType != "[Unidentified Device]") device.deviceType else existing.deviceType,
                        vendorName = if (device.vendorName != "[Unknown Hardware]") device.vendorName else existing.vendorName,
                        macAddress = device.macAddress ?: existing.macAddress,
                        openPorts = (existing.openPorts + device.openPorts).distinct(),
                        httpTitle = device.httpTitle ?: existing.httpTitle,
                        lastSeen = System.currentTimeMillis()
                    )
                    _foundDevices.remove(existing)
                }
                _foundDevices.add(updatedDevice)
            }

            // Refine OS fingerprint if generic and active probes are allowed
            if (updatedDevice.osFingerprint.contains("Linux-based") || updatedDevice.osFingerprint.contains("Unknown")) {
                if (updatedDevice.openPorts.isNotEmpty()) {
                    val refinedOs = DeviceResolver.fingerprintOs(
                        ip = updatedDevice.ipAddress,
                        openPorts = updatedDevice.openPorts,
                        serviceType = updatedDevice.deviceType,
                        hostname = updatedDevice.deviceName
                    )
                    if (refinedOs != updatedDevice.osFingerprint) {
                        updatedDevice = updatedDevice.copy(osFingerprint = refinedOs)
                        synchronized(_foundDevices) {
                            _foundDevices.removeIf { it.ipAddress == updatedDevice.ipAddress }
                            _foundDevices.add(updatedDevice)
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                onDeviceFound(updatedDevice)
            }
        }
    }

    private fun createDiscoveryListener(
        serviceType: String,
        onEvent: (ScanEvent) -> Unit,
        getMac: (String) -> String?,
        onDeviceFound: (Device) -> Unit
    ): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) { }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { }
                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                        val ip = resolvedInfo.host?.hostAddress ?: return
                        val type = DeviceResolver.resolveTypeByService(serviceType)
                        val name = resolvedInfo.serviceName ?: ""
                        val mac = getMac(ip)
                        val vendor = MacVendorResolver.resolve(mac)
                        
                        val device = Device(
                            ipAddress = ip,
                            macAddress = mac,
                            vendorName = vendor,
                            hostname = name,
                            deviceName = name,
                            deviceType = type
                        )
                        updateDeviceList(device, onDeviceFound)
                    }
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) { }
            override fun onDiscoveryStopped(regType: String) { }
            override fun onStartDiscoveryFailed(regType: String, errorCode: Int) { }
            override fun onStopDiscoveryFailed(regType: String, errorCode: Int) { }
        }
    }

    private fun getLocalIpAddress(): String? {
        val ipInt = wifiManager.connectionInfo.ipAddress
        return if (ipInt == 0) null else {
            String.format("%d.%d.%d.%d", ipInt and 0xff, ipInt shr 8 and 0xff, ipInt shr 16 and 0xff, ipInt shr 24 and 0xff)
        }
    }
}

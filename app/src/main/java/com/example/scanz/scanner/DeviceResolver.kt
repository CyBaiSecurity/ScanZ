package com.example.scanz.scanner

import java.net.InetAddress
import java.util.Locale

object DeviceResolver {

    /**
     * Resolves device identity using strict Reverse DNS lookup.
     * Returns Pair(deviceName, deviceType)
     */
    fun resolveByDns(ipAddress: String): Pair<String, String> {
        try {
            val address = InetAddress.getByName(ipAddress)
            val canonicalHostName = address.canonicalHostName ?: ""
            
            // Validation: If hostname is just IP or IP-formatted, discard it
            if (isGenericHostname(canonicalHostName, ipAddress)) {
                return "" to "[Unidentified Device]"
            }

            val type = inferTypeFromHostname(canonicalHostName)
            return canonicalHostName to type
        } catch (e: Exception) {
            return "" to "[Unidentified Device]"
        }
    }

    /**
     * Maps NSD Service types to strict Device types.
     */
    fun resolveTypeByService(serviceType: String): String {
        return when {
            serviceType.contains("_ipp") || serviceType.contains("_pdl-datastream") -> "[Printer]"
            serviceType.contains("_googlecast") || serviceType.contains("_airplay") -> "[Media/Smart TV]"
            else -> "[Unidentified Device]"
        }
    }

    private fun isGenericHostname(hostname: String, ip: String): Boolean {
        if (hostname.isEmpty()) return true
        if (hostname == ip) return true
        
        // Check for hyphenated IP addresses (e.g., 192-168-1-1)
        val hyphenatedIp = ip.replace(".", "-")
        if (hostname.contains(hyphenatedIp)) return true
        
        return false
    }

    private fun inferTypeFromHostname(hostname: String): String {
        val lower = hostname.lowercase(Locale.ROOT)
        return when {
            lower.contains("iphone") || lower.contains("ipad") || lower.contains("android") -> "[Mobile]"
            lower.contains("macbook") || lower.contains("desktop") || lower.contains("pc") || lower.contains("laptop") -> "[PC]"
            lower.contains("epson") || lower.contains("hp-laserjet") || lower.contains("brother") || lower.contains("printer") -> "[Printer]"
            else -> "[Unidentified Device]"
        }
    }

    fun formatMac(mac: String?): String {
        return if (mac.isNullOrEmpty() || mac == "02:00:00:00:00:00") {
            "[MAC: RESTRICTED BY OS]"
        } else {
            mac
        }
    }

    /**
     * Executes a native ping and fingerprints the OS based on TTL values and L4/L7 context.
     */
    suspend fun fingerprintOs(
        ip: String,
        openPorts: List<Int> = emptyList(),
        serviceType: String? = null,
        hostname: String? = null
    ): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("ping -c 1 -W 1 $ip")
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val ttlRegex = Regex("ttl=(\\d+)", RegexOption.IGNORE_CASE)
            val match = ttlRegex.find(output)
            
            if (match != null) {
                val ttl = match.groupValues[1].toInt()
                val lowerHost = hostname?.lowercase(Locale.ROOT) ?: ""

                when {
                    ttl <= 64 -> {
                        // Refine Unix-like OS
                        when {
                            lowerHost.contains("android") || serviceType?.contains("googlecast") == true -> "[OS: Android]"
                            lowerHost.contains("iphone") || lowerHost.contains("ipad") || serviceType?.contains("airplay") == true -> "[OS: iOS]"
                            lowerHost.contains("mac") || lowerHost.contains("apple") -> "[OS: macOS]"
                            openPorts.contains(22) -> "[OS: Linux / Server]"
                            else -> "[OS: Linux-based]"
                        }
                    }
                    ttl <= 128 -> {
                        // Windows typically uses 128
                        "[OS: Windows]"
                    }
                    ttl <= 255 -> {
                        "[OS: Network Infrastructure / Router]"
                    }
                    else -> "[OS: Unknown]"
                }
            } else {
                "[OS: Unknown]"
            }
        } catch (e: Exception) {
            "[OS: Unknown]"
        }
    }
}

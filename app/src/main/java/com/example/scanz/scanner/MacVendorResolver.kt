package com.example.scanz.scanner

import java.util.Locale

object MacVendorResolver {

    // Base dictionary of common OUIs
    private var baseOuiMap = mutableMapOf(
        // === VIRTUAL MACHINES & CONTAINERS ===
        "00:50:56" to "VMware",
        "00:0C:29" to "VMware",
        "00:05:69" to "VMware",
        "08:00:27" to "Oracle (VirtualBox)",
        "00:16:3E" to "XenSource",
        "52:54:00" to "QEMU/KVM",
        "00:15:5D" to "Microsoft (Hyper-V)",

        // === ENTERPRISE NETWORKING (Switches/Routers) ===
        "00:00:0C" to "Cisco Systems",
        "00:01:42" to "Cisco Systems",
        "00:01:43" to "Cisco Systems",
        "00:1B:D4" to "Cisco Systems",
        "00:23:CD" to "Cisco Systems",
        "04:18:D6" to "Ubiquiti Networks",
        "24:A4:3C" to "Ubiquiti Networks",
        "44:D9:E7" to "Ubiquiti Networks",
        "00:10:E3" to "Juniper Networks",
        "00:19:E2" to "Juniper Networks",
        "00:0B:86" to "Aruba Networks",
        "20:4C:03" to "Aruba Networks",
        "00:50:F0" to "Kramer Electronics",
        "CC:D5:39" to "MikroTik",
        "E4:8D:8C" to "MikroTik",

        // === CONSUMER NETWORKING (Wi-Fi/Routers) ===
        "14:CC:20" to "TP-Link",
        "50:C7:BF" to "TP-Link",
        "C0:25:E9" to "TP-Link",
        "00:14:BF" to "Linksys",
        "00:1A:62" to "Linksys",
        "00:14:6C" to "Netgear",
        "00:1E:2A" to "Netgear",
        "00:24:B2" to "Netgear",
        "00:1E:E5" to "Cisco/Linksys",
        "00:11:F5" to "D-Link",
        "00:17:9A" to "D-Link",

        // === APPLE (Phones/Macs/Tablets) ===
        "00:05:02" to "Apple, Inc.",
        "00:0A:95" to "Apple, Inc.",
        "00:1C:B3" to "Apple, Inc.",
        "00:23:12" to "Apple, Inc.",
        "14:C2:13" to "Apple, Inc.",
        "28:CF:E9" to "Apple, Inc.",
        "34:15:9E" to "Apple, Inc.",
        "3C:22:FB" to "Apple, Inc.",
        "58:CB:52" to "Apple, Inc.",
        "60:F8:1D" to "Apple, Inc.",
        "70:3E:AC" to "Apple, Inc.",
        "84:38:35" to "Apple, Inc.",
        "8C:85:90" to "Apple, Inc.",
        "AC:29:3A" to "Apple, Inc.",
        "C0:A0:BB" to "Apple, Inc.",
        "D4:61:9D" to "Apple, Inc.",
        "D8:30:62" to "Apple, Inc.",
        "E4:E4:AB" to "Apple, Inc.",
        "F8:1E:DF" to "Apple, Inc.",

        // === ANDROID & MOBILE MANUFACTURERS ===
        "00:12:36" to "Samsung Electronics",
        "14:89:FD" to "Samsung Electronics",
        "24:18:1D" to "Samsung Electronics",
        "64:A2:F9" to "Samsung Electronics",
        "A4:77:33" to "Samsung Electronics",
        "B0:C0:90" to "Samsung Electronics",
        "CC:B1:1A" to "Samsung Electronics",
        "00:1E:10" to "Huawei",
        "00:46:4B" to "Huawei",
        "48:46:FB" to "Huawei",
        "00:9E:C8" to "Xiaomi",
        "14:F6:5A" to "Xiaomi",
        "28:E3:1F" to "Xiaomi",
        "00:1A:11" to "Google",
        "48:D6:D5" to "Google",
        "50:78:B3" to "Google",
        "54:60:09" to "Google",
        "F4:F5:D8" to "Google",
        "C0:EE:FB" to "OnePlus",
        "00:1E:8C" to "Motorola",
        "F8:CF:C5" to "Motorola",
        "00:0F:1C" to "Sony Ericsson",
        "00:1D:BA" to "Sony",
        "E0:CB:4E" to "ASUSTek (Phones/PCs)",
        "00:23:76" to "HTC",

        // === PCs, LAPTOPS & MOTHERBOARDS ===
        "00:14:22" to "Dell Inc.",
        "14:FE:B5" to "Dell Inc.",
        "F8:BC:12" to "Dell Inc.",
        "00:1D:60" to "ASUSTek Computer",
        "40:1C:83" to "Intel Corporation",
        "00:24:D7" to "Intel Corporation",
        "F8:16:54" to "Intel Corporation",
        "00:1F:3B" to "Intel Corporation",
        "00:14:A5" to "Hewlett Packard",
        "00:1A:4B" to "Hewlett Packard",
        "00:23:68" to "Centrino (Intel)",
        "00:1E:68" to "Acer",
        "00:16:D3" to "Lenovo",
        "00:23:14" to "Intel",
        "00:25:D3" to "Lenovo",
        "00:1F:16" to "Wistron (Lenovo/Dell OEM)",
        "00:E0:4C" to "Realtek Semiconductor",
        "52:54:00" to "Realtek Semiconductor",
        "00:10:18" to "Broadcom",
        "00:1A:A6" to "Gigabyte Technology",
        "D8:50:E6" to "ASUSTek Computer",

        // === PRINTERS & OFFICE EQUIPMENT ===
        "00:01:E6" to "Hewlett Packard (Printer)",
        "00:0E:7F" to "Hewlett Packard (Printer)",
        "00:11:0A" to "Hewlett Packard (Printer)",
        "3F:DF:53" to "Hewlett Packard (Printer)",
        "00:1B:A9" to "Brother Industries",
        "00:80:77" to "Brother Industries",
        "80:1F:02" to "Edimax (Print Servers)",
        "00:00:48" to "Seiko Epson",
        "00:26:AB" to "Seiko Epson",
        "00:00:85" to "Canon",
        "00:1E:8F" to "Canon",
        "00:00:AA" to "Xerox",
        "00:20:83" to "Lexmark",

        // === IoT, SMART HOME & MEDIA ===
        "DC:A6:32" to "Raspberry Pi",
        "B8:27:EB" to "Raspberry Pi",
        "E4:5F:01" to "Raspberry Pi",
        "F0:27:2D" to "Amazon (Echo/Kindle)",
        "FC:A1:83" to "Amazon (Echo/Kindle)",
        "44:65:0D" to "Amazon (Echo/Kindle)",
        "18:B4:30" to "Nest Labs",
        "00:17:88" to "Philips (Hue)",
        "00:0E:58" to "Sonos",
        "B8:E9:37" to "Sonos",
        "D8:D5:B9" to "Roku",
        "B0:EE:45" to "Roku",
        "00:04:4B" to "NVIDIA (Shield)",
        "00:24:E4" to "Withings",

        // === GAMING CONSOLES ===
        "00:19:C5" to "Nintendo (Wii)",
        "9C:E6:35" to "Nintendo (Switch)",
        "CC:FB:65" to "Nintendo (Switch)",
        "00:04:19" to "Sony (PlayStation)",
        "00:D9:D1" to "Sony (PlayStation)",
        "50:1D:93" to "Microsoft (Xbox)",
        "7C:ED:8D" to "Microsoft (Xbox)"
    )

    // User-defined dictionary for persistent overrides
    private val customOuiMap = mutableMapOf<String, String>()

    fun updateVendorDictionary(newEntries: Map<String, String>) {
        customOuiMap.putAll(newEntries)
    }

    fun resolve(mac: String?): String {
        if (mac.isNullOrEmpty()) return "[Unknown Hardware]"

        // 1. Sanitize to standard XX:XX:XX uppercase format
        val cleanMac = mac.uppercase(Locale.ROOT).replace("-", ":")
        if (cleanMac.length < 8) return "[Unknown Hardware]"

        // 2. CRITICAL FEATURE: Detect MAC Randomization
        // Probability logic: if 2nd char is 2, 6, A, or E, it's 90% likely a private mobile MAC.
        if (cleanMac.length >= 2) {
            val secondChar = cleanMac[1]
            if (secondChar == '2' || secondChar == '6' || secondChar == 'A' || secondChar == 'E') {
                return "[ Phone (90% Private) ]"
            }
        }

        // 3. O(1) Lookup: Check Custom first, then Base
        val oui = cleanMac.substring(0, 8)
        return customOuiMap[oui] ?: baseOuiMap[oui] ?: "[Unknown Hardware]"
    }

    fun getOui(mac: String?): String? {
        if (mac.isNullOrEmpty()) return null
        val cleanMac = mac.uppercase(Locale.ROOT).replace("-", ":")
        return if (cleanMac.length >= 8) cleanMac.substring(0, 8) else null
    }
}

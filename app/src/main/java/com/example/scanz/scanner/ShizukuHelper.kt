package com.example.scanz.scanner

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method

object ShizukuHelper {

    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun checkPermission(): Boolean {
        return if (Shizuku.isPreV11()) {
            false
        } else {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermission(requestCode: Int) {
        if (Shizuku.isPreV11()) return
        try {
            Shizuku.requestPermission(requestCode)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.addRequestPermissionResultListener(listener)
        } catch (e: Exception) {}
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.removeRequestPermissionResultListener(listener)
        } catch (e: Exception) {}
    }

    /**
     * Resolves the system ARP cache using Shizuku and Reflection to bypass
     * private API restrictions in Shizuku v13.
     */
    suspend fun getArpTable(): Map<String, String> = withContext(Dispatchers.IO) {
        if (!isAvailable() || !checkPermission()) return@withContext emptyMap()

        val arpMap = mutableMapOf<String, String>()
        try {
            // Reflection bypass for private Shizuku.newProcess method
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val method: Method = clazz.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true

            // Invoke 'sh -c ip neigh show' via Shizuku's ADB UID
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", "ip neigh show"),
                null,
                null
            ) as ShizukuRemoteProcess

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            
            // Regex to match IP and MAC (lladdr)
            // Example output: 192.168.1.1 dev wlan0 lladdr 00:11:22:33:44:55 REACHABLE
            val regex = Regex("""^([\d.]+)\s+.*lladdr\s+([a-fA-F\d:]{17}).*""")

            reader.useLines { lines ->
                lines.forEach { line ->
                    val match = regex.find(line)
                    if (match != null) {
                        val ip = match.groupValues[1]
                        val mac = match.groupValues[2]
                        arpMap[ip] = mac
                    }
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        arpMap
    }
}

package com.example.scanz.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class Device(
    @PrimaryKey val ipAddress: String,
    val macAddress: String?,
    val hostname: String?,
    val deviceName: String = "",
    val deviceType: String = "[Unidentified Device]",
    val vendorName: String = "[Unknown Vendor]",
    val openPorts: List<Int> = emptyList(),
    val httpTitle: String? = null,
    val osFingerprint: String = "[OS: Unknown]",
    val isCustomVendor: Boolean = false,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis()
)

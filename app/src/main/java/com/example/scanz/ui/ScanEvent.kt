package com.example.scanz.ui

data class ScanEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val type: String,
    val target: String,
    val details: String,
    val threatWeight: Int
)

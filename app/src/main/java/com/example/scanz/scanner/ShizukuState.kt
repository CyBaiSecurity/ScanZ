package com.example.scanz.scanner

import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

object ShizukuState {
    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    enum class ConnectionStatus {
        DISCONNECTED, DAEMON_NOT_RUNNING, NEEDS_AUTH, CONNECTED
    }

    fun updateStatus() {
        try {
            if (!Shizuku.pingBinder()) {
                _status.value = ConnectionStatus.DAEMON_NOT_RUNNING
                return
            }

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                _status.value = ConnectionStatus.CONNECTED
            } else {
                _status.value = ConnectionStatus.NEEDS_AUTH
            }
        } catch (e: Exception) {
            android.util.Log.e("ScanZ_Debug", "Shizuku Crash: ${e.message}", e)
            _status.value = ConnectionStatus.DAEMON_NOT_RUNNING
        }
    }

    fun setDisconnected() {
        _status.value = ConnectionStatus.DISCONNECTED
    }

    fun forceConnected() {
        _status.value = ConnectionStatus.CONNECTED
    }
}
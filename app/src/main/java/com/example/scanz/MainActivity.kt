package com.example.scanz

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.scanz.scanner.ShizukuState
import com.example.scanz.ui.ScannerScreen
import com.example.scanz.ui.theme.ScanZTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        ShizukuState.updateStatus()

        // CRITICAL FIX: If Shizuku is running but we aren't authorized, ask for it!
        if (ShizukuState.status.value == ShizukuState.ConnectionStatus.NEEDS_AUTH) {
            Shizuku.requestPermission(0)
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        ShizukuState.setDisconnected()
    }

    // Listens for your tap on the "Allow" pop-up
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            ShizukuState.forceConnected()
        } else {
            ShizukuState.setDisconnected()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. MUST use Sticky to catch the daemon if it was already running before the app opened
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        ShizukuState.updateStatus()

        setContent {
            ScanZTheme {
                // 2. Wrap in a Surface. This forces the screen to use your dark theme
                // background instead of defaulting to pure system white.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ScannerScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }
}
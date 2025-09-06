package com.yusuke.drk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val locationPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 画面側で状態を再評価 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val ui by vm.ui.collectAsState()
                    MainScreen(
                        ui = ui,
                        onRequestPermission = { permissionLauncher.launch(locationPermissions) },
                        onStart = { startTracking(); vm.start() },
                        onStop = { stopTracking(); vm.stop() },
                        hasPermission = { hasLocationPermission() }
                    )
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        return fine && coarse && notifOk
    }

    private fun startTracking() {
        val intent = Intent(this, DrKTrackingService::class.java).apply { action = DrKTrackingService.ACTION_START }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopTracking() {
        val intent = Intent(this, DrKTrackingService::class.java).apply { action = DrKTrackingService.ACTION_STOP }
        startService(intent)
    }
}

@Composable
private fun MainScreen(
    ui: UiState,
    onRequestPermission: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    hasPermission: () -> Boolean
) {
    var granted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { granted = hasPermission() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Dr.K 計測中間実装（距離/時間/ペース）")
        Text(text = "距離: ${ui.distanceKmText} km")
        Text(text = "時間: ${ui.elapsedText}")
        Text(text = "平均ペース: ${ui.paceText}")
        if (!granted) {
            Button(onClick = {
                onRequestPermission()
                granted = hasPermission()
            }) { Text(text = "権限を許可") }
        }
        Button(onClick = onStart, enabled = granted && !ui.isTracking) { Text(text = "開始") }
        Button(onClick = onStop, enabled = ui.isTracking) { Text(text = "終了") }
    }
}

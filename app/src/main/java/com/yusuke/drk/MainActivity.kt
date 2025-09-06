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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.model.LatLng
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.yusuke.drk.data.TrackingRepository
import kotlinx.coroutines.launch

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
    val vm: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val points by vm.observeCurrentTrackPoints().collectAsState(initial = emptyList())
    val poly = remember(points) {
        points.map { LatLng(it.lat, it.lon) }
    }
    val lastLatLng = poly.lastOrNull()
    val cameraState = rememberCameraPositionState()
    LaunchedEffect(lastLatLng) {
        lastLatLng?.let { ll ->
            cameraState.move(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(ll, 16f))
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Dr.K 計測中間実装（距離/時間/ペース）")
        Text(text = "距離: ${ui.distanceKmText}")
        Text(text = "時間: ${ui.elapsedText}")
        Text(text = "平均ペース: ${ui.paceText}")
        if (!granted) {
            Button(onClick = {
                onRequestPermission()
                granted = hasPermission()
            }) { Text(text = "権限を許可") }
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        val miles by com.yusuke.drk.data.SettingsRepository.unitMiles(context).collectAsState(initial = false)
        Button(onClick = onStart, enabled = granted && !ui.isTracking) { Text(text = "開始") }
        Button(onClick = onStop, enabled = ui.isTracking) { Text(text = "終了") }
        val scope = rememberCoroutineScope()
        Button(onClick = { scope.launch { com.yusuke.drk.data.SettingsRepository.setUnitMiles(context, !miles) } }) {
            Text(text = "単位: ${if (miles) "mi" else "km"}")
        }
        if (granted) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    uiSettings = MapUiSettings(zoomControlsEnabled = false),
                    cameraPositionState = cameraState
                ) {
                    if (poly.size >= 2) {
                        Polyline(points = poly)
                    }
                }
            }
        }
    }
    // リザルト表示（停止後）
    var result by remember { mutableStateOf<TrackingRepository.ResultEvent?>(null) }
    LaunchedEffect(Unit) {
        TrackingRepository.results.collect { evt -> result = evt }
    }
    result?.let { evt ->
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text("リザルト") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val comp by rememberLottieComposition(LottieCompositionSpec.RawRes(if (evt.levelUp) R.raw.level_up else R.raw.title_unlocked))
                    LottieAnimation(composition = comp)
                    Text("獲得XP: ${evt.earnedXp}")
                    if (evt.levelUp) Text("レベルアップ！")
                    if (evt.newTitles.isNotEmpty()) Text("称号獲得: ${evt.newTitles.joinToString()}")
                }
            },
            confirmButton = {
                TextButton(onClick = { result = null }) { Text("OK") }
            }
        )
    }

    // 補助モーダル: カレンダー（直近30日）
    var showCalendar by remember { mutableStateOf(false) }
    var showTitles by remember { mutableStateOf(false) }
    // 画面下に簡易ボタン追加
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = { showCalendar = true }) { Text("カレンダー") }
        Button(onClick = { showTitles = true }) { Text("称号/プロフィール") }
    }

    if (showCalendar) {
        val today = java.time.LocalDate.now()
        val from = today.minusDays(29).toString()
        val to = today.toString()
        val stats by vm.observeDailyRange(from, to).collectAsState(initial = emptyList())
        AlertDialog(
            onDismissRequest = { showCalendar = false },
            title = { Text("直近30日の合計") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    stats.forEach { s ->
                        val km = s.totalDistanceM / 1000.0
                        Text("${s.date}: 距離 ${String.format("%.2f", km)} km / 時間 ${formatHmsStatic(s.totalDurationS)}")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCalendar = false }) { Text("閉じる") } }
        )
    }

    if (showTitles) {
        val ps by produceState<com.yusuke.drk.data.PlayerState?>(initialValue = null, showTitles) {
            value = vm.getPlayerState()
        }
        val defs by produceState<List<com.yusuke.drk.data.TitleDef>>(initialValue = emptyList(), showTitles) {
            value = vm.getTitleDefs()
        }
        val owned = ps?.titlesCsv?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        AlertDialog(
            onDismissRequest = { showTitles = false },
            title = { Text("称号/プロフィール") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("レベル: ${ps?.level ?: 1}  XP: ${ps?.totalXp ?: 0}/${ps?.nextLevelXp ?: 100}")
                    Text("連続日数: ${ps?.streakDays ?: 0}")
                    Text("称号")
                    defs.forEach { d ->
                        val mark = if (owned.contains(d.key)) "✅" else "⬜"
                        Text("$mark ${d.name}")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTitles = false }) { Text("閉じる") } }
        )
    }
}

private fun formatHmsStatic(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}

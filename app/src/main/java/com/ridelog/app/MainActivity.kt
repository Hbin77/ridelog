package com.ridelog.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ridelog.app.trip.Phase
import com.ridelog.app.ui.ActiveParkedScreen
import com.ridelog.app.ui.DebugPanel
import com.ridelog.app.ui.DrivingIdleScreen
import com.ridelog.app.ui.DrivingScreen
import com.ridelog.app.ui.HistoryScreen
import com.ridelog.app.ui.SettingsScreen
import com.ridelog.app.ui.HomeScreen
import com.ridelog.app.ui.ReviewScreen
import com.ridelog.app.ui.StartTripScreen
import com.ridelog.app.ui.theme.RideLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        // 기록은 Activity가 아니라 Application 범위에서 계속된다 (화면이 바뀌어도 끊기지 않음)
        val controller = (application as RideLogApp).controller
        setContent {
            RideLogTheme {
                val state by controller.ui.collectAsState()
                var picking by rememberSaveable { mutableStateOf(false) }
                var debug by rememberSaveable { mutableStateOf(false) }
                var page by rememberSaveable { mutableStateOf("home") }
                val history by controller.history.collectAsState()
                when {
                    // 개발자 패널은 데모용 — 가상 주행/정차를 직접 누를 수 있게 주행 중에도 유지
                    debug -> DebugPanel(controller) { debug = false }
                    // 주행 중에는 무조건 음성 전용 화면 (금액·목록·키보드 표시 금지)
                    state.driving && state.phase == Phase.ACTIVE -> DrivingScreen(state, controller::listen)
                    // 주행 중에는 정산표·설정·좌석 배정 등 금액·입력 화면을 열지 않는다
                    state.driving -> DrivingIdleScreen()
                    state.phase == Phase.ACTIVE -> ActiveParkedScreen(state, controller::listen, controller::endTrip) { debug = true }
                    state.phase == Phase.REVIEW -> ReviewScreen(state, controller) { controller.reset() }
                    picking -> StartTripScreen(controller.settings.members, { controller.startTrip(it); picking = false }) { picking = false }
                    page == "settings" -> SettingsScreen(controller.settings) { page = "home" }
                    page == "history" -> HistoryScreen(history) { page = "home" }
                    else -> HomeScreen(
                        starting = state.starting,
                        onStart = { picking = true },
                        onHistory = { page = "history" },
                        onSettings = { page = "settings" },
                        onDebug = { debug = true },
                    )
                }
            }
        }
    }
}

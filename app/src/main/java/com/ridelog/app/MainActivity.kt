package com.ridelog.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.ridelog.app.trip.Phase
import com.ridelog.app.trip.TripController
import com.ridelog.app.ui.ActiveParkedScreen
import com.ridelog.app.ui.DebugPanel
import com.ridelog.app.ui.DrivingScreen
import com.ridelog.app.ui.HomeScreen
import com.ridelog.app.ui.ReviewScreen
import com.ridelog.app.ui.StartTripScreen
import com.ridelog.app.ui.theme.RideLogTheme
import com.ridelog.app.vehicle.FakeVehicleDataSource
import com.ridelog.app.vehicle.PleosVehicleDataSource
import com.ridelog.app.voice.VehicleSpeechIn
import com.ridelog.app.voice.VehicleSpeechOut

class MainActivity : ComponentActivity() {
    private lateinit var controller: TripController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        controller = TripController(
            context = applicationContext,
            scope = lifecycleScope,
            fake = FakeVehicleDataSource(),
            pleos = PleosVehicleDataSource(applicationContext, lifecycleScope),
            speechOut = VehicleSpeechOut(applicationContext),
            speechIn = VehicleSpeechIn(applicationContext),
        )
        setContent {
            RideLogTheme {
                val state by controller.ui.collectAsState()
                var picking by remember { mutableStateOf(false) }
                var debug by remember { mutableStateOf(false) }
                when {
                    debug && !state.driving -> DebugPanel(controller) { debug = false }
                    // 주행 중에는 무조건 음성 전용 화면 (금액·목록·키보드 표시 금지)
                    state.driving && state.phase == Phase.ACTIVE -> DrivingScreen(state, controller::listen)
                    state.phase == Phase.ACTIVE -> ActiveParkedScreen(state, controller::listen, controller::endTrip) { debug = true }
                    state.phase == Phase.REVIEW -> ReviewScreen(state, controller) { controller.reset() }
                    picking -> StartTripScreen(controller.settings.members, { controller.startTrip(it); picking = false }) { picking = false }
                    else -> HomeScreen(onStart = { picking = true }, onDebug = { debug = true })
                }
            }
        }
    }

    override fun onDestroy() {
        controller.pleos.stop()
        super.onDestroy()
    }
}

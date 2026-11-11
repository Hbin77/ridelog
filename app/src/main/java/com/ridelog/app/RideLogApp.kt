package com.ridelog.app

import android.app.Application
import com.ridelog.app.trip.TripController
import com.ridelog.app.vehicle.FakeVehicleDataSource
import com.ridelog.app.vehicle.PleosVehicleDataSource
import com.ridelog.app.voice.VehicleSpeechIn
import com.ridelog.app.voice.VehicleSpeechOut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 여행 기록·차량 리스너·음성 엔진을 프로세스당 하나만 둔다. */
class RideLogApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val controller: TripController by lazy {
        TripController(
            context = this,
            scope = appScope,
            fake = FakeVehicleDataSource(),
            pleos = PleosVehicleDataSource(this, appScope),
            speechOut = VehicleSpeechOut(this),
            speechIn = VehicleSpeechIn(this),
        )
    }
}

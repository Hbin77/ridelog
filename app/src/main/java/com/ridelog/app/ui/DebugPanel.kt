package com.ridelog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.ridelog.app.trip.TripController
import com.ridelog.app.ui.theme.Ride
import com.ridelog.app.ui.theme.RideType
import com.ridelog.app.ui.theme.Spacing
import com.ridelog.core.model.SeatId

/** 개발자 패널: Pleos 신호 검증(스파이크)과 데모용 가상 신호 주입. 제목을 눌러 연다. */
@Composable
fun DebugPanel(controller: TripController, onClose: () -> Unit) {
    val c = Ride.colors
    val caps by controller.pleos.capabilities.collectAsState()
    val pleosLog by controller.pleos.log.collectAsState()
    val fake = controller.fake
    Column(
        Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("개발자 패널", style = RideType.title, color = c.onBackground, modifier = Modifier.weight(1f))
            PrimaryButton("패널 닫기", onClose)
        }
        Text("음성 출력: ${controller.speechOutName} / 음성 인식: ${controller.speechInName}", style = RideType.label, color = c.onSurfaceMuted)
        Text("Pleos 지원 신호: ${caps.joinToString().ifEmpty { "없음" }}", style = RideType.label, color = c.onSurfaceMuted)

        Text("가상 신호 (데모)", style = RideType.body, color = c.onBackground)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            PrimaryButton("주행", { fake.setDriving(true) }, Modifier.weight(1f), secondary = true)
            PrimaryButton("정차", { fake.setDriving(false) }, Modifier.weight(1f), secondary = true)
            PrimaryButton("+20km", { fake.drive(20.0) }, Modifier.weight(1f), secondary = true)
            PrimaryButton("충전 90%", { fake.charge(90.0) }, Modifier.weight(1f), secondary = true)
        }
        listOf(SeatId.FRONT_PASSENGER, SeatId.REAR_LEFT, SeatId.REAR_RIGHT).forEach { seat ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                PrimaryButton("${seat.label} 탑승", { fake.setOccupied(seat, true); fake.setBuckled(seat, true) }, Modifier.weight(1f), secondary = true)
                PrimaryButton("하차", { fake.setOccupied(seat, false) }, Modifier.weight(1f), secondary = true)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            PrimaryButton("음성: 간식 3만 2천원 내가", { controller.handleUtterance("휴게소 간식 3만 2천원 내가 냈어") }, Modifier.weight(1f), secondary = true)
            PrimaryButton("음성: 주차비 만원 민수가", { controller.handleUtterance("주차비 만원 민수가 냈어") }, Modifier.weight(1f), secondary = true)
        }

        Text("Pleos 로그", style = RideType.body, color = c.onBackground)
        pleosLog.forEach { Text(it, style = RideType.label, color = c.onSurfaceMuted) }
    }
}

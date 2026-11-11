package com.ridelog.app.trip

import android.content.Context
import com.ridelog.core.model.Member

/** 저장된 동승 멤버와 정산 설정. 기기 안에만 저장한다. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ridelog", Context.MODE_PRIVATE)

    var pricePerKwhWon: Double
        get() = prefs.getFloat("price", 347.2f).toDouble()
        set(v) = prefs.edit().putFloat("price", v.toFloat()).apply()

    var defaultKmPerKwh: Double
        get() = prefs.getFloat("defaultEff", 5.0f).toDouble()
        set(v) = prefs.edit().putFloat("defaultEff", v.toFloat()).apply()

    val batteryCapacityKwh: Double?
        get() = prefs.getFloat("capacity", 77.4f).toDouble()

    var account: String
        get() = prefs.getString("account", "") ?: ""
        set(v) = prefs.edit().putString("account", v).apply()

    var forceFake: Boolean
        get() = prefs.getBoolean("forceFake", false)
        set(v) = prefs.edit().putBoolean("forceFake", v).apply()

    var members: List<Member>
        get() {
            val raw = prefs.getString("members", null) ?: return DEFAULT_MEMBERS
            return raw.split("\n").filter { it.isNotBlank() }.mapIndexed { i, name -> Member("m$i", name) }
        }
        set(v) = prefs.edit().putString("members", v.joinToString("\n") { it.name }).apply()

    private companion object {
        val DEFAULT_MEMBERS = listOf("나", "민수", "서연", "지호").mapIndexed { i, n -> Member("m$i", n) }
    }
}

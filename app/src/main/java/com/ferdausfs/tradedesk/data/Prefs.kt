package com.ferdausfs.tradedesk.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Alert(val symbol: String, val above: Boolean, val price: Double, val fired: Boolean = false)

class Prefs(ctx: Context) {

    private val sp: SharedPreferences =
        ctx.getSharedPreferences("tradedesk", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val strList = ListSerializer(String.serializer())
    private val alertList = ListSerializer(Alert.serializer())

    private val defaultWatch = listOf(
        "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "DOGEUSDT", "XRPUSDT"
    )

    var symbol: String
        get() = sp.getString("symbol", "BTCUSDT") ?: "BTCUSDT"
        set(v) = sp.edit().putString("symbol", v).apply()

    var interval: String
        get() = sp.getString("interval", "15m") ?: "15m"
        set(v) = sp.edit().putString("interval", v).apply()

    var emaOn: Boolean
        get() = sp.getBoolean("emaOn", true)
        set(v) = sp.edit().putBoolean("emaOn", v).apply()

    var bbOn: Boolean
        get() = sp.getBoolean("bbOn", false)
        set(v) = sp.edit().putBoolean("bbOn", v).apply()

    fun watchlist(): List<String> {
        val raw = sp.getString("watchlist", null) ?: return defaultWatch
        return runCatching { json.decodeFromString(strList, raw) }.getOrDefault(defaultWatch)
    }

    fun setWatchlist(v: List<String>) {
        sp.edit().putString("watchlist", json.encodeToString(strList, v)).apply()
    }

    fun alerts(): List<Alert> {
        val raw = sp.getString("alerts", null) ?: return emptyList()
        return runCatching { json.decodeFromString(alertList, raw) }.getOrDefault(emptyList())
    }

    fun setAlerts(v: List<Alert>) {
        sp.edit().putString("alerts", json.encodeToString(alertList, v)).apply()
    }
}

package com.ferdausfs.tradedesk.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class Candle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

class Binance {

    private val http = OkHttpClient.Builder()
        .pingInterval(Duration.ofSeconds(20))
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val rest = "https://data-api.binance.vision"

    suspend fun klines(symbol: String, interval: String, limit: Int = 500): List<Candle> =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$rest/api/v3/klines?symbol=$symbol&interval=$interval&limit=$limit")
                .build()
            val body = http.newCall(req).execute().use { it.body?.string() ?: "[]" }
            json.parseToJsonElement(body).jsonArray.map { e ->
                val a = e.jsonArray
                Candle(
                    a[0].jsonPrimitive.long,
                    a[1].jsonPrimitive.content.toDouble(),
                    a[2].jsonPrimitive.content.toDouble(),
                    a[3].jsonPrimitive.content.toDouble(),
                    a[4].jsonPrimitive.content.toDouble(),
                    a[5].jsonPrimitive.content.toDouble()
                )
            }
        }

    suspend fun symbols(): List<String> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$rest/api/v3/exchangeInfo").build()
        val body = http.newCall(req).execute().use { it.body?.string() ?: "{}" }
        val root = json.parseToJsonElement(body).jsonObject
        val out = mutableListOf<String>()
        root["symbols"]?.jsonArray?.forEach { s ->
            val o = s.jsonObject
            val status = o["status"]?.jsonPrimitive?.content
            val quote = o["quoteAsset"]?.jsonPrimitive?.content
            val sym = o["symbol"]?.jsonPrimitive?.content
            if (status == "TRADING" && quote == "USDT" && sym != null) out.add(sym)
        }
        out.sorted()
    }

    suspend fun tickers(symbols: List<String>): Map<String, Pair<Double, Double>> =
        withContext(Dispatchers.IO) {
            if (symbols.isEmpty()) return@withContext emptyMap()
            val q = symbols.joinToString("\",\"", "[\"", "\"]")
            val req = Request.Builder()
                .url("$rest/api/v3/ticker/24hr?symbols=$q")
                .build()
            val body = http.newCall(req).execute().use { it.body?.string() ?: "[]" }
            val out = mutableMapOf<String, Pair<Double, Double>>()
            runCatching {
                json.parseToJsonElement(body).jsonArray.forEach { e ->
                    val o = e.jsonObject
                    val sym = o["symbol"]?.jsonPrimitive?.content ?: return@forEach
                    out[sym] = Pair(
                        o["lastPrice"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                        o["priceChangePercent"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    )
                }
            }
            out
        }

    fun klineFlow(symbol: String, interval: String): Flow<Candle> = channelFlow {
        val socket = KlineSocket(
            "wss://stream.binance.vision/ws/${symbol.lowercase()}@kline_${interval}"
        ) { c -> trySend(c) }
        socket.start(http)
        awaitClose { socket.stop() }
    }
}

private class KlineSocket(
    private val url: String,
    private val onCandle: (Candle) -> Unit
) : WebSocketListener() {

    private val exec = Executors.newSingleThreadScheduledExecutor()
    private val json = Json { ignoreUnknownKeys = true }
    private var client: OkHttpClient? = null
    private var ws: WebSocket? = null
    private var stopped = false

    fun start(http: OkHttpClient) {
        client = http
        open()
    }

    fun stop() {
        stopped = true
        exec.shutdownNow()
        runCatching { ws?.close(1000, "bye") }
    }

    private fun open() {
        val c = client ?: return
        ws = c.newWebSocket(Request.Builder().url(url).build(), this)
    }

    private fun reconnect() {
        if (stopped) return
        exec.schedule({
            if (!stopped) runCatching { open() }
        }, 3L, TimeUnit.SECONDS)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            val k = json.parseToJsonElement(text).jsonObject["k"]?.jsonObject ?: return
            val c = Candle(
                k["t"]?.jsonPrimitive?.long ?: return,
                k["o"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return,
                k["h"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return,
                k["l"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return,
                k["c"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return,
                k["v"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return
            )
            onCandle(c)
        } catch (_: Exception) {
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        reconnect()
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        reconnect()
    }
}

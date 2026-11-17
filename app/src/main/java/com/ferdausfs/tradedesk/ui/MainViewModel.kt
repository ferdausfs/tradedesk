package com.ferdausfs.tradedesk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ferdausfs.tradedesk.data.TrendLine
import com.ferdausfs.tradedesk.data.Alert
import com.ferdausfs.tradedesk.data.Binance
import com.ferdausfs.tradedesk.data.Candle
import com.ferdausfs.tradedesk.data.Prefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Ticker(val symbol: String, val last: Double, val changePct: Double)

class MainViewModel(private val prefs: Prefs) : ViewModel() {

    private val binance = Binance()

    private val _symbol = MutableStateFlow(prefs.symbol)
    val symbol: StateFlow<String> = _symbol.asStateFlow()
    private val _interval = MutableStateFlow(prefs.interval)
    val interval: StateFlow<String> = _interval.asStateFlow()
    private val _candles = MutableStateFlow<List<Candle>>(emptyList())
    val candles: StateFlow<List<Candle>> = _candles.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _ticker = MutableStateFlow<Ticker?>(null)
    val ticker: StateFlow<Ticker?> = _ticker.asStateFlow()
    private val _emaOn = MutableStateFlow(prefs.emaOn)
    val emaOn: StateFlow<Boolean> = _emaOn.asStateFlow()
    private val _bbOn = MutableStateFlow(prefs.bbOn)
    val bbOn: StateFlow<Boolean> = _bbOn.asStateFlow()
    private val _rsiOn = MutableStateFlow(prefs.rsiOn)
    val rsiOn: StateFlow<Boolean> = _rsiOn.asStateFlow()
    private val _watchlist = MutableStateFlow(prefs.watchlist())
    val watchlist: StateFlow<List<String>> = _watchlist.asStateFlow()
    private val _tickers = MutableStateFlow<Map<String, Ticker>>(emptyMap())
    val tickers: StateFlow<Map<String, Ticker>> = _tickers.asStateFlow()
    private val _alerts = MutableStateFlow(prefs.alerts())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()
    private val _allSymbols = MutableStateFlow<List<String>>(emptyList())
    val allSymbols: StateFlow<List<String>> = _allSymbols.asStateFlow()

    private val _replay = MutableStateFlow(false)
    val replay: StateFlow<Boolean> = _replay.asStateFlow()
    private val _replayIdx = MutableStateFlow(0)
    val replayIdx: StateFlow<Int> = _replayIdx.asStateFlow()
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()
    private var replayJob: Job? = null

    private val _lines = MutableStateFlow<List<TrendLine>>(emptyList())
    val lines: StateFlow<List<TrendLine>> = _lines.asStateFlow()
    private val _drawMode = MutableStateFlow(false)
    val drawMode: StateFlow<Boolean> = _drawMode.asStateFlow()

    var onAlertFired: ((String, Double) -> Unit)? = null

    private var wsJob: Job? = null

    init {
        load()
        loadSymbols()
    }

    fun setInterval(v: String) {
        if (v == _interval.value) return
        exitReplay()
        _interval.value = v
        prefs.interval = v
        load()
    }

    fun setSymbol(v: String) {
        if (v == _symbol.value) return
        exitReplay()
        _symbol.value = v
        prefs.symbol = v
        _ticker.value = null
        load()
    }

    fun toggleEma() {
        _emaOn.value = !_emaOn.value
        prefs.emaOn = _emaOn.value
    }

    fun toggleBb() {
        _bbOn.value = !_bbOn.value
        prefs.bbOn = _bbOn.value
    }

    fun toggleRsi() {
        _rsiOn.value = !_rsiOn.value
        prefs.rsiOn = _rsiOn.value
    }

    fun toggleDraw() {
        _drawMode.value = !_drawMode.value
    }

    fun addLine(x1: Float, p1: Double, x2: Float, p2: Double) {
        val key = _symbol.value + "|" + _interval.value
        val nv = _lines.value + TrendLine(x1, p1, x2, p2)
        _lines.value = nv
        prefs.setLinesFor(key, nv)
        _drawMode.value = false
    }

    fun undoLine() {
        val key = _symbol.value + "|" + _interval.value
        if (_lines.value.isNotEmpty()) {
            val nv = _lines.value.dropLast(1)
            _lines.value = nv
            prefs.setLinesFor(key, nv)
        }
    }

    fun clearLines() {
        val key = _symbol.value + "|" + _interval.value
        _lines.value = emptyList()
        prefs.setLinesFor(key, emptyList())
    }

    fun toggleReplay() {
        if (!_replay.value) {
            val n = _candles.value.size
            if (n < 40) return
            _replay.value = true
            isPlaying = false
            replayJob?.cancel()
            _replayIdx.value = (n * 0.7f).toInt().coerceIn(30, n - 1)
        } else {
            exitReplay()
        }
    }

    private var isPlaying = false

    private fun exitReplay() {
        _replay.value = false
        isPlaying = false
        _playing.value = false
        replayJob?.cancel()
    }

    fun playPauseReplay() {
        isPlaying = !isPlaying
        _playing.value = isPlaying
        replayJob?.cancel()
        if (isPlaying) {
            replayJob = viewModelScope.launch {
                while (isPlaying && _replay.value && _replayIdx.value < _candles.value.size) {
                    _replayIdx.value = _replayIdx.value + 1
                    delay(400)
                }
            }
        }
    }

    fun stepReplay() {
        isPlaying = false
        _playing.value = false
        replayJob?.cancel()
        _replayIdx.value = (_replayIdx.value + 1).coerceAtMost(_candles.value.size)
    }

    fun resetReplay() {
        isPlaying = false
        _playing.value = false
        replayJob?.cancel()
        val n = _candles.value.size
        _replayIdx.value = (n * 0.7f).toInt().coerceIn(30, (n - 1).coerceAtLeast(1))
    }

    fun addWatch(s: String) {
        val cur = _watchlist.value
        if (s !in cur) {
            val nv = cur + s
            _watchlist.value = nv
            prefs.setWatchlist(nv)
        }
    }

    fun removeWatch(s: String) {
        val nv = _watchlist.value - s
        _watchlist.value = nv
        prefs.setWatchlist(nv)
    }

    fun addAlert(a: Alert) {
        val nv = _alerts.value + a
        _alerts.value = nv
        prefs.setAlerts(nv)
    }

    fun removeAlert(a: Alert) {
        val nv = _alerts.value - a
        _alerts.value = nv
        prefs.setAlerts(nv)
    }

    fun loadSymbols() {
        if (_allSymbols.value.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { binance.symbols() }.onSuccess { _allSymbols.value = it }
        }
    }

    fun refreshWatchTickers() {
        viewModelScope.launch {
            val syms = _watchlist.value
            runCatching { binance.tickers(syms) }.onSuccess { m ->
                _tickers.value = m.mapValues { Ticker(it.key, it.value.first, it.value.second) }
                applyAlerts(m.mapValues { it.value.first })
            }
        }
    }

    private fun load() {
        wsJob?.cancel()
        _loading.value = true
        val sym = _symbol.value
        val itv = _interval.value
        _lines.value = prefs.linesFor(sym + "|" + itv)
        _drawMode.value = false
        viewModelScope.launch {
            runCatching { binance.klines(sym, itv) }.onSuccess { list ->
                if (sym == _symbol.value && itv == _interval.value) {
                    _candles.value = list
                    _loading.value = false
                    wsJob?.cancel()
                    wsJob = viewModelScope.launch {
                        binance.klineFlow(sym, itv).collect { c ->
                            if (_replay.value) return@collect
                            val cur = _candles.value
                            if (cur.isEmpty()) {
                                _candles.value = listOf(c)
                            } else if (c.openTime == cur.last().openTime) {
                                _candles.value = cur.dropLast(1) + c
                            } else if (c.openTime > cur.last().openTime) {
                                val nv = cur + c
                                _candles.value = if (nv.size > 1500) nv.takeLast(1500) else nv
                            }
                            checkAlerts(c.close)
                        }
                    }
                }
            }.onFailure {
                _loading.value = false
            }
        }
        viewModelScope.launch {
            runCatching { binance.tickers(listOf(sym)) }.onSuccess { m ->
                m[sym]?.let {
                    if (sym == _symbol.value) _ticker.value = Ticker(sym, it.first, it.second)
                }
            }
        }
    }

    private fun checkAlerts(price: Double) {
        applyAlerts(mapOf(_symbol.value to price))
    }

    private fun applyAlerts(prices: Map<String, Double>) {
        val cur = _alerts.value
        var changed = false
        val updated = cur.map { a ->
            if (!a.fired) {
                val p = prices[a.symbol]
                if (p != null) {
                    val hit = if (a.above) p >= a.price else p <= a.price
                    if (hit) {
                        changed = true
                        onAlertFired?.invoke(a.symbol, p)
                        a.copy(fired = true)
                    } else {
                        a
                    }
                } else {
                    a
                }
            } else {
                a
            }
        }
        if (changed) {
            _alerts.value = updated
            prefs.setAlerts(updated)
        }
    }
}

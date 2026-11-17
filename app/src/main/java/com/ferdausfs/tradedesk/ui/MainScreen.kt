package com.ferdausfs.tradedesk.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ferdausfs.tradedesk.chart.CandleChart
import com.ferdausfs.tradedesk.chart.ChartUi
import com.ferdausfs.tradedesk.chart.fmtPrice
import com.ferdausfs.tradedesk.data.Alert
import com.ferdausfs.tradedesk.data.bollinger
import com.ferdausfs.tradedesk.data.ema
import com.ferdausfs.tradedesk.data.rsi
import com.ferdausfs.tradedesk.data.Prefs
import kotlinx.coroutines.delay
import java.util.Locale

private val INTERVALS = listOf("1m", "5m", "15m", "1h", "4h", "1d", "1w")

@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    val vm = remember { MainViewModel(Prefs(ctx)) }

    LaunchedEffect(Unit) {
        vm.onAlertFired = { sym, price ->
            Notify.fire(ctx, "TradeDesk — $sym", "Price crossed ${fmtPrice(price)}")
        }
    }

    var tab by remember { mutableStateOf(0) }
    var showSearch by remember { mutableStateOf(false) }
    var showIndicators by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = PanelC) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.CandlestickChart, contentDescription = null) },
                    label = { Text("Chart") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    label = { Text("Watchlist") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    label = { Text("Alerts") }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Bg)) {
            when (tab) {
                0 -> ChartTab(
                    vm,
                    onOpenSearch = { showSearch = true },
                    onOpenIndicators = { showIndicators = true }
                )
                1 -> WatchTab(vm) { tab = 0 }
                2 -> AlertsTab(vm)
            }
        }
    }

    if (showSearch) SearchSheet(vm) { showSearch = false }
    if (showIndicators) IndicatorsSheet(vm) { showIndicators = false }
}

@Composable
private fun ChartTab(vm: MainViewModel, onOpenSearch: () -> Unit, onOpenIndicators: () -> Unit) {
    val symbol by vm.symbol.collectAsState()
    val interval by vm.interval.collectAsState()
    val candles by vm.candles.collectAsState()
    val loading by vm.loading.collectAsState()
    val ticker by vm.ticker.collectAsState()
    val emaOn by vm.emaOn.collectAsState()
    val bbOn by vm.bbOn.collectAsState()
    val rsiOn by vm.rsiOn.collectAsState()
    val watchlist by vm.watchlist.collectAsState()
    val replay by vm.replay.collectAsState()
    val replayIdx by vm.replayIdx.collectAsState()
    val playingReplay by vm.playing.collectAsState()
    val lines by vm.lines.collectAsState()
    val drawMode by vm.drawMode.collectAsState()

    val displayCandles =
        if (replay) candles.take(replayIdx.coerceAtLeast(1)) else candles
    val lastC = displayCandles.lastOrNull()
    val livePrice = lastC?.close ?: ticker?.last ?: 0.0
    val priceColor =
        if (ticker == null) TextC else if ((ticker?.changePct ?: 0.0) >= 0) UpC else DownC
    val watched = symbol in watchlist

    Column(Modifier.fillMaxSize()) {

        // header: symbol | price | favorite star
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier.clickable { onOpenSearch() }.padding(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(symbol, color = TextC, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MutedC)
                }
                Text("Binance · Spot", color = MutedC, fontSize = 10.sp)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(fmtPrice(livePrice), color = priceColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(fmtPct(ticker?.changePct), color = pctColor(ticker?.changePct), fontSize = 11.sp)
            }
            IconButton(onClick = {
                if (watched) vm.removeWatch(symbol) else vm.addWatch(symbol)
            }) {
                Icon(
                    if (watched) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = null,
                    tint = if (watched) AccentC else MutedC
                )
            }
        }

        // timeframe chips
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            INTERVALS.forEach { itv ->
                val sel = itv == interval
                Text(
                    itv,
                    color = if (sel) Bg else TextC,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .background(if (sel) AccentC else PanelC, MaterialTheme.shapes.small)
                        .clickable { vm.setInterval(itv) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Text(
            text = if (lastC != null)
                "O ${fmtPrice(lastC.open)}  H ${fmtPrice(lastC.high)}  L ${fmtPrice(lastC.low)}  C ${fmtPrice(lastC.close)}  V ${fmtVol(lastC.volume)}"
            else " ",
            color = MutedC,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val chartData = remember(displayCandles, emaOn, bbOn, rsiOn, interval) {
                val closes = displayCandles.map { it.close }
                val bb =
                    if (bbOn) bollinger(closes)
                    else Triple(
                        emptyList<Double?>(), emptyList<Double?>(), emptyList<Double?>()
                    )
                ChartUi(
                    candles = displayCandles,
                    ema = if (emaOn) ema(closes, 21) else emptyList(),
                    bbUpper = if (bbOn) bb.first else emptyList(),
                    bbLower = if (bbOn) bb.third else emptyList(),
                    rsi = if (rsiOn) rsi(closes) else emptyList(),
                    daily = interval == "1d" || interval == "1w"
                )
            }
            CandleChart(
                chartData,
                lines = lines,
                drawMode = drawMode,
                onDrawLine = { x1, p1, x2, p2 -> vm.addLine(x1, p1, x2, p2) },
                Modifier.fillMaxSize()
            )
            if (loading) {
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center).size(28.dp),
                    color = AccentC, strokeWidth = 2.dp
                )
            }
            if (drawMode) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallBtn("Undo") { vm.undoLine() }
                    Spacer(Modifier.width(4.dp))
                    SmallBtn("Clear") { vm.clearLines() }
                }
            }

            // floating tool buttons (right edge)
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FabMini(Icons.Filled.Tune, false) { onOpenIndicators() }
                FabMini(Icons.Filled.Replay, replay) { vm.toggleReplay() }
                FabMini(Icons.Filled.Edit, drawMode) { vm.toggleDraw() }
            }

            // replay controls (bottom overlay)
            if (replay) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .background(PanelC.copy(alpha = 0.95f), MaterialTheme.shapes.small)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallBtn("⟲") { vm.resetReplay() }
                    Spacer(Modifier.width(4.dp))
                    SmallBtn(if (playingReplay) "⏸" else "▶") { vm.playPauseReplay() }
                    Spacer(Modifier.width(4.dp))
                    SmallBtn("▶▶") { vm.stepReplay() }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${replayIdx.coerceAtMost(candles.size)} / ${candles.size}",
                        color = MutedC, fontSize = 11.sp
                    )
                    Spacer(Modifier.weight(1f))
                    SmallBtn("Exit") { vm.toggleReplay() }
                }
            }
        }
    }
}

@Composable
private fun WatchTab(vm: MainViewModel, onOpenChart: () -> Unit) {
    val watchlist by vm.watchlist.collectAsState()
    val tickers by vm.tickers.collectAsState()
    val allSymbols by vm.allSymbols.collectAsState()
    var q by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.loadSymbols() }
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshWatchTickers()
            delay(10_000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            label = { Text("Search & add symbol…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
        val res = remember(q, allSymbols) {
            if (q.isBlank()) emptyList()
            else allSymbols.filter { it.contains(q.trim().uppercase()) }.take(30)
        }
        if (res.isNotEmpty()) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                items(res) { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.addWatch(s); q = "" }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(s, color = TextC)
                        Spacer(Modifier.weight(1f))
                        Text("add", color = AccentC, fontSize = 12.sp)
                    }
                }
            }
        }
        Text(
            "Watchlist — tap to open chart",
            color = MutedC, fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(watchlist) { s ->
                val t = tickers[s]
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setSymbol(s); onOpenChart() }
                        .background(if (s == vm.symbol.value) PanelC else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(s, color = TextC, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (t == null) "—" else fmtPrice(t.last),
                            color = TextC, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (t == null) "" else fmtPct(t.changePct),
                            color = pctColor(t?.changePct), fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = { vm.removeWatch(s) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = MutedC, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertsTab(vm: MainViewModel) {
    val ctx = LocalContext.current
    val symbol by vm.symbol.collectAsState()
    val alerts by vm.alerts.collectAsState()
    var priceText by remember { mutableStateOf("") }
    var above by remember { mutableStateOf(true) }
    val permLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Price alerts — $symbol",
            color = TextC, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            modifier = Modifier.padding(12.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("Price") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (above) "Above" else "Below",
                color = TextC,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Switch(checked = above, onCheckedChange = { above = it })
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val p = priceText.toDoubleOrNull() ?: return@Button
                vm.addAlert(Alert(symbol, above, p))
                priceText = ""
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }) { Text("Add") }
        }
        Text(
            "Alerts check live prices while the app is running",
            color = MutedC, fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(alerts) { a ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "${a.symbol} ${if (a.above) "≥" else "≤"} ${fmtPrice(a.price)}",
                            color = if (a.fired) MutedC else TextC, fontSize = 14.sp
                        )
                        if (a.fired) {
                            Text("triggered — remove & re-add to reuse", color = MutedC, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.removeAlert(a) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MutedC, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IndicatorsSheet(vm: MainViewModel, onClose: () -> Unit) {
    val emaOn by vm.emaOn.collectAsState()
    val bbOn by vm.bbOn.collectAsState()
    val rsiOn by vm.rsiOn.collectAsState()
    ModalBottomSheet(onDismissRequest = onClose, containerColor = PanelC) {
        Text(
            "Indicators", color = TextC, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            modifier = Modifier.padding(16.dp)
        )
        SheetRow("EMA (21)", emaOn) { vm.toggleEma() }
        SheetRow("Bollinger Bands (20, 2)", bbOn) { vm.toggleBb() }
        SheetRow("RSI (14)", rsiOn) { vm.toggleRsi() }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SheetRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextC, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSheet(vm: MainViewModel, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val allSymbols by vm.allSymbols.collectAsState()
    LaunchedEffect(Unit) { vm.loadSymbols() }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = PanelC) {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            label = { Text("Symbol (e.g. BTC)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
        val res = remember(q, allSymbols) {
            if (q.isBlank()) allSymbols.take(40)
            else allSymbols.filter { it.contains(q.trim().uppercase()) }.take(60)
        }
        if (allSymbols.isEmpty() && res.isEmpty()) {
            Text("Loading symbols…", color = MutedC, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
            items(res) { s ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setSymbol(s); onClose() }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(s, color = TextC)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FabMini(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (active) AccentC else PanelC.copy(alpha = 0.92f),
        onClick = onClick,
        modifier = Modifier.padding(vertical = 5.dp)
    ) {
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = if (active) Bg else TextC, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SmallBtn(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = TextC,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(PanelC, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

private fun fmtPct(p: Double?): String =
    if (p == null) "" else String.format(Locale.US, "%+.2f%%", p)

private fun pctColor(p: Double?): Color = if ((p ?: 0.0) >= 0) UpC else DownC

private fun fmtVol(v: Double): String = when {
    v >= 1_000_000 -> String.format(Locale.US, "%.2fM", v / 1_000_000)
    v >= 1_000 -> String.format(Locale.US, "%.1fK", v / 1_000)
    else -> String.format(Locale.US, "%.2f", v)
}

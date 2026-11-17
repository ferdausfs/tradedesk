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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
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
import com.ferdausfs.tradedesk.data.Prefs
import kotlinx.coroutines.delay
import java.util.Locale

private val INTERVALS = listOf("1m", "5m", "15m", "1h", "4h", "1d", "1w")

@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    val vm = remember { MainViewModel(Prefs(ctx)) }

    val symbol by vm.symbol.collectAsState()
    val interval by vm.interval.collectAsState()
    val candles by vm.candles.collectAsState()
    val loading by vm.loading.collectAsState()
    val ticker by vm.ticker.collectAsState()
    val emaOn by vm.emaOn.collectAsState()
    val bbOn by vm.bbOn.collectAsState()
    val watchlist by vm.watchlist.collectAsState()
    val tickers by vm.tickers.collectAsState()
    val allSymbols by vm.allSymbols.collectAsState()
    val alerts by vm.alerts.collectAsState()

    var sheet by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.onAlertFired = { sym, price ->
            Notify.fire(ctx, "TradeDesk — $sym", "Price crossed ${fmtPrice(price)}")
        }
    }

    LaunchedEffect(sheet, watchlist) {
        if (sheet == "watch") {
            while (true) {
                vm.refreshWatchTickers()
                delay(10_000)
            }
        }
    }

    val lastC = candles.lastOrNull()
    val livePrice = lastC?.close ?: ticker?.last ?: 0.0
    val priceColor = if (ticker == null) TextC else if ((ticker?.changePct ?: 0.0) >= 0) UpC else DownC

    Column(Modifier.fillMaxSize().background(Bg)) {

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                symbol,
                color = TextC,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.clickable { sheet = "search" }.padding(6.dp)
            )
            Column(Modifier.padding(start = 6.dp)) {
                Text(fmtPrice(livePrice), color = priceColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(fmtPct(ticker?.changePct), color = pctColor(ticker?.changePct), fontSize = 10.sp)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { sheet = "alerts" }) {
                Icon(Icons.Filled.Notifications, contentDescription = null, tint = TextC)
            }
            IconButton(onClick = { sheet = "watch" }) {
                Icon(Icons.Filled.List, contentDescription = null, tint = TextC)
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
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
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            IndicatorChip("EMA", emaOn) { vm.toggleEma() }
            IndicatorChip("BB", bbOn) { vm.toggleBb() }
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
            val chartData = remember(candles, emaOn, bbOn, interval) {
                val closes = candles.map { it.close }
                val bb = if (bbOn) bollinger(closes) else Triple(emptyList<Double?>(), emptyList<Double?>(), emptyList<Double?>())
                ChartUi(
                    candles = candles,
                    ema = if (emaOn) ema(closes, 21) else emptyList(),
                    bbUpper = if (bbOn) bb.first else emptyList(),
                    bbLower = if (bbOn) bb.third else emptyList(),
                    daily = interval == "1d" || interval == "1w"
                )
            }
            CandleChart(chartData, Modifier.fillMaxSize())
            if (loading) {
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center).size(28.dp),
                    color = AccentC, strokeWidth = 2.dp
                )
            }
        }
    }

    when (sheet) {
        "search" -> SearchSheet(vm, allSymbols) { sheet = null }
        "watch" -> WatchSheet(vm, watchlist, tickers, allSymbols) { sheet = null }
        "alerts" -> AlertsSheet(vm, symbol, alerts) { sheet = null }
    }
}

@Composable
private fun IndicatorChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) Bg else MutedC,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(end = 6.dp)
            .background(if (active) AccentC else PanelC, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSheet(vm: MainViewModel, all: List<String>, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadSymbols() }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = PanelC) {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            label = { Text("Symbol (e.g. BTC)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
        val res = remember(q, all) {
            if (q.isBlank()) all.take(40)
            else all.filter { it.contains(q.trim().uppercase()) }.take(60)
        }
        if (all.isEmpty() && res.isEmpty()) {
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
private fun WatchSheet(
    vm: MainViewModel,
    watch: List<String>,
    tickers: Map<String, Ticker>,
    all: List<String>,
    onClose: () -> Unit
) {
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadSymbols() }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = PanelC) {
        Text(
            "Watchlist", color = TextC, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.padding(12.dp)
        )
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            label = { Text("Add symbol…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
        val res = remember(q, all) {
            if (q.isBlank()) emptyList()
            else all.filter { it.contains(q.trim().uppercase()) }.take(40)
        }
        if (res.isNotEmpty()) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
                items(res) { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.addWatch(s); onClose() }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(s, color = TextC)
                        Spacer(Modifier.weight(1f))
                        Text("add", color = AccentC, fontSize = 12.sp)
                    }
                }
            }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.dp)) {
            items(watch) { s ->
                val t = tickers[s]
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setSymbol(s); onClose() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(s, color = TextC, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (t == null) "—" else fmtPrice(t.last),
                            color = TextC, fontSize = 13.sp
                        )
                        Text(
                            if (t == null) "" else fmtPct(t.changePct),
                            color = pctColor(t?.changePct), fontSize = 10.sp
                        )
                    }
                    IconButton(onClick = { vm.removeWatch(s) }) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = MutedC)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertsSheet(vm: MainViewModel, symbol: String, alerts: List<Alert>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var priceText by remember(symbol) { mutableStateOf("") }
    var above by remember { mutableStateOf(true) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    ModalBottomSheet(onDismissRequest = onClose, containerColor = PanelC) {
        Text(
            "Price alerts — $symbol",
            color = TextC, fontWeight = FontWeight.Bold, fontSize = 14.sp,
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
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            items(alerts) { a ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "${a.symbol} ${if (a.above) "≥" else "≤"} ${fmtPrice(a.price)}",
                            color = if (a.fired) MutedC else TextC, fontSize = 13.sp
                        )
                        if (a.fired) {
                            Text("triggered — remove & re-add to reuse", color = MutedC, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.removeAlert(a) }) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MutedC)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun fmtPct(p: Double?): String =
    if (p == null) "" else String.format(Locale.US, "%+.2f%%", p)

private fun pctColor(p: Double?): Color = if ((p ?: 0.0) >= 0) UpC else DownC

private fun fmtVol(v: Double): String = when {
    v >= 1_000_000 -> String.format(Locale.US, "%.2fM", v / 1_000_000)
    v >= 1_000 -> String.format(Locale.US, "%.1fK", v / 1_000)
    else -> String.format(Locale.US, "%.2f", v)
}

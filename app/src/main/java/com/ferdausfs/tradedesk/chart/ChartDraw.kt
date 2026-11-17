package com.ferdausfs.tradedesk.chart

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ferdausfs.tradedesk.data.Candle
import com.ferdausfs.tradedesk.data.TrendLine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChartUi(
    val candles: List<Candle>,
    val ema: List<Double?> = emptyList(),
    val bbUpper: List<Double?> = emptyList(),
    val bbLower: List<Double?> = emptyList(),
    val rsi: List<Double?> = emptyList(),
    val daily: Boolean = false,
    val intervalSec: Int = 900
)

// light cream chart palette (own implementation)
private val GridC = Color(0xFFE2D7BE)
private val AxisC = Color(0xFF787B86)
private val CrossC = Color(0xFF9598A1)
private val BodyUpC = Color.White        // up candle: white body
private val DownBodyC = Color(0xFF131722) // down candle: black body
private val BorderC = Color(0xFF131722)  // black wicks + borders
private val EmaC = Color(0xFFE8A33D)
private val BbC = Color(0xFF2962FF)
private val RsiC = Color(0xFF8E24AA)
private val DrawC = Color(0xFF2962FF)
private val TagBg = Color(0xFFC8CBD6)
private val TagText = Color(0xFF131722)

fun fmtPrice(v: Double): String = when {
    v >= 1000 -> String.format(Locale.US, "%.2f", v)
    v >= 1 -> String.format(Locale.US, "%.4f", v)
    else -> String.format(Locale.US, "%.6f", v)
}

private fun fmtCountdown(sec: Long): String = when {
    sec >= 86400 -> String.format(Locale.US, "%dd %dh", sec / 86400, (sec % 86400) / 3600)
    sec >= 3600 -> String.format(Locale.US, "%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
    else -> String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
}

private class ViewParams {
    var left = 0f
    var priceTop = 0f
    var priceH = 1f
    var loP = 0.0
    var hiP = 1.0
    var px = 8f
    var count = 0
}

@Composable
fun CandleChart(
    data: ChartUi,
    lines: List<TrendLine> = emptyList(),
    drawMode: Boolean = false,
    onDrawLine: (Float, Double, Float, Double) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {

    val density = LocalDensity.current
    val d = density.density
    val minPx = with(density) { 2.dp.toPx() }
    val maxPx = with(density) { 40.dp.toPx() }
    val startPx = with(density) { 8.dp.toPx() }

    var pxPer by remember { mutableFloatStateOf(startPx) }
    var rightIdx by remember { mutableFloatStateOf(0f) }
    var follow by remember { mutableStateOf(true) }
    var cross by remember { mutableStateOf<Offset?>(null) }
    var preview by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    var w by remember { mutableIntStateOf(0) }
    var h by remember { mutableIntStateOf(0) }
    val vp = remember { ViewParams() }

    val labelPaint = remember {
        Paint().apply {
            color = AxisC.toArgb()
            textSize = 10f * d
            isAntiAlias = true
        }
    }
    val rsiPaint = remember {
        Paint().apply {
            color = RsiC.toArgb()
            textSize = 10f * d
            isAntiAlias = true
        }
    }
    val tagBgPaint = remember { Paint().apply { color = TagBg.toArgb() } }
    val tagTextPaint = remember {
        Paint().apply {
            color = TagText.toArgb()
            textSize = 10f * d
            isAntiAlias = true
        }
    }
    val tagPricePaint = remember {
        Paint().apply {
            color = TagText.toArgb()
            textSize = 11f * d
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }
    val tagCntPaint = remember {
        Paint().apply {
            color = TagText.toArgb()
            textSize = 9f * d
            isAntiAlias = true
        }
    }
    val tf = remember(data.daily) {
        if (data.daily) SimpleDateFormat("MMM d", Locale.US)
        else SimpleDateFormat("HH:mm", Locale.US)
    }

    fun priceAt(y: Float): Double {
        val yy = y.coerceIn(vp.priceTop, vp.priceTop + vp.priceH)
        return vp.loP + (vp.priceTop + vp.priceH - yy) / vp.priceH * (vp.hiP - vp.loP)
    }

    fun idxAt(x: Float): Float =
        (vp.left + x / vp.px).coerceIn(0f, (vp.count - 1).coerceAtLeast(0).toFloat())

    Canvas(
        modifier
            .onSizeChanged { w = it.width; h = it.height }
            .pointerInput(drawMode) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    cross = down.position
                    if (drawMode) {
                        val start = down.position
                        var cur = start
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            cur = pressed[0].position
                            cross = cur
                            preview = Pair(start, cur)
                            ev.changes.forEach { it.consume() }
                        }
                        preview = null
                        val dx = cur.x - start.x
                        val dy = cur.y - start.y
                        if (dx * dx + dy * dy > 400f && vp.count > 0) {
                            val p1 = priceAt(start.y)
                            val p2 = priceAt(cur.y)
                            if (p1.isFinite() && p2.isFinite()) {
                                onDrawLine(idxAt(start.x), p1, idxAt(cur.x), p2)
                            }
                        }
                        cross = null
                    } else {
                        var prevX = down.position.x
                        var prevDist = 0f
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                val a = pressed[0].position
                                val b = pressed[1].position
                                val dist = (a - b).getDistance().coerceAtLeast(1f)
                                if (prevDist > 0f) {
                                    val f = (dist / prevDist).coerceIn(0.6f, 1.6f)
                                    pxPer = (pxPer * f).coerceIn(minPx, maxPx)
                                }
                                prevDist = dist
                                prevX = pressed[0].position.x
                            } else {
                                val p = pressed[0]
                                val dx = p.position.x - prevX
                                prevX = p.position.x
                                if (prevDist > 0f) prevDist = 0f
                                val vis = w / pxPer
                                val base = if (follow) data.candles.size.toFloat() else rightIdx
                                val r = base - dx / pxPer
                                if (r >= data.candles.size) {
                                    follow = true
                                } else {
                                    follow = false
                                    rightIdx = r.coerceIn(vis * 0.2f, data.candles.size + vis)
                                }
                            }
                            cross = pressed.last().position
                            ev.changes.forEach { it.consume() }
                        }
                        cross = null
                    }
                }
            }
    ) {
        val list = data.candles
        if (list.isEmpty() || w == 0 || h == 0) return@Canvas

        val hasRsi = data.rsi.isNotEmpty()
        val volH = h * 0.14f
        val rsiH = if (hasRsi) h * 0.13f else 0f
        val axisB = 18f * d
        val priceTop = 6f * d
        val priceH = (h - volH - rsiH - axisB - priceTop - 8f * d).coerceAtLeast(10f)
        val priceBottom = priceTop + priceH
        val volBase = priceBottom + 4f * d + volH
        val rsiTop = volBase + (if (hasRsi) 6f * d else 0f)
        val rsiBottom = rsiTop + rsiH
        val crossBottom = if (hasRsi) rsiBottom else volBase
        val widthF = w.toFloat()

        val vis = widthF / pxPer
        val left = (if (follow) list.size - vis else rightIdx - vis)
            .coerceIn(-5f, (list.size - vis).coerceAtLeast(-5f))
        val first = left.toInt().coerceIn(0, list.size - 1)
        val lastI = (left + vis + 1).toInt().coerceAtMost(list.size - 1)

        var hi = -Double.MAX_VALUE
        var lo = Double.MAX_VALUE
        for (i in first..lastI) {
            val c = list[i]
            if (c.high > hi) hi = c.high
            if (c.low < lo) lo = c.low
        }
        val pad = ((hi - lo).coerceAtLeast(1e-9)) * 0.06
        val hiP = hi + pad
        val loP = lo - pad

        fun yOf(v: Double): Float =
            (priceBottom - ((v - loP) / (hiP - loP)) * priceH).toFloat()

        fun xOfF(i: Float): Float = ((i + 0.5f - left) * pxPer)
        fun xOf(i: Int): Float = xOfF(i.toFloat())

        vp.left = left
        vp.priceTop = priceTop
        vp.priceH = priceH
        vp.loP = loP
        vp.hiP = hiP
        vp.px = pxPer
        vp.count = list.size

        // horizontal grid + price labels
        val steps = 4
        for (s in 0..steps) {
            val v = loP + (hiP - loP) * s / steps
            val yy = yOf(v)
            drawLine(GridC, Offset(0f, yy), Offset(widthF, yy), 1f)
            val txt = fmtPrice(v)
            val tw = labelPaint.measureText(txt)
            drawIntoCanvas { it.nativeCanvas.drawText(txt, widthF - tw - 6f, yy - 4f, labelPaint) }
        }

        // volume bars
        var maxVol = 0.0
        for (i in first..lastI) if (list[i].volume > maxVol) maxVol = list[i].volume
        val bodyW = (pxPer * 0.62f).coerceAtLeast(1f)
        for (i in first..lastI) {
            val c = list[i]
            val vh = if (maxVol > 0) (c.volume / maxVol * (volH - 2f)).toFloat() else 0f
            drawRect(
                BorderC.copy(alpha = 0.25f),
                Offset(xOf(i) - bodyW / 2, volBase - vh),
                Size(bodyW, vh)
            )
        }

        // candles: white bodies with black border (up) / solid black (down)
        for (i in first..lastI) {
            val c = list[i]
            val up = c.close >= c.open
            val fill = if (up) BodyUpC else DownBodyC
            val xx = xOf(i)
            val yH = yOf(c.high)
            val yL = yOf(c.low)
            val yO = yOf(c.open)
            val yC = yOf(c.close)
            val top = minOf(yO, yC)
            val bot = maxOf(yO, yC)
            drawLine(BorderC, Offset(xx, yH), Offset(xx, maxOf(yL, top)), 1f)
            if (bot - top < 1.5f) {
                val t2 = top - 0.5f
                drawRect(fill, Offset(xx - bodyW / 2, t2), Size(bodyW, 1.5f))
                drawRect(BorderC, Offset(xx - bodyW / 2, t2), Size(bodyW, 1.5f), style = Stroke(1f))
            } else {
                drawRect(fill, Offset(xx - bodyW / 2, top), Size(bodyW, bot - top))
                drawRect(BorderC, Offset(xx - bodyW / 2, top), Size(bodyW, bot - top), style = Stroke(1f))
            }
        }

        // overlay lines (EMA / Bollinger)
        fun pathOf(vals: List<Double?>, color: Color, width: Float) {
            if (vals.isEmpty()) return
            val p = Path()
            var started = false
            for (i in first..lastI) {
                val v = vals.getOrNull(i) ?: continue
                if (!started) {
                    p.moveTo(xOf(i), yOf(v))
                    started = true
                } else {
                    p.lineTo(xOf(i), yOf(v))
                }
            }
            if (started) drawPath(p, color, style = Stroke(width, cap = StrokeCap.Round))
        }
        pathOf(data.ema, EmaC, 1.6f)
        if (data.bbUpper.isNotEmpty() || data.bbLower.isNotEmpty()) {
            pathOf(data.bbUpper, BbC, 1.2f)
            pathOf(data.bbLower, BbC, 1.2f)
        }

        // RSI pane
        if (hasRsi) {
            fun yR(v: Double): Float = (rsiBottom - (v / 100.0) * rsiH).toFloat()
            drawLine(GridC, Offset(0f, rsiTop - 3f * d), Offset(widthF, rsiTop - 3f * d), 1f)
            drawLine(GridC.copy(alpha = 0.6f), Offset(0f, yR(70.0)), Offset(widthF, yR(70.0)), 1f)
            drawLine(GridC.copy(alpha = 0.6f), Offset(0f, yR(30.0)), Offset(widthF, yR(30.0)), 1f)
            val p = Path()
            var started = false
            for (i in first..lastI) {
                val v = data.rsi.getOrNull(i) ?: continue
                if (!started) {
                    p.moveTo(xOf(i), yR(v))
                    started = true
                } else {
                    p.lineTo(xOf(i), yR(v))
                }
            }
            if (started) drawPath(p, RsiC, style = Stroke(1.4f, cap = StrokeCap.Round))
            drawIntoCanvas {
                val lv = data.rsi.getOrNull(lastI)
                val txt = "RSI(14)" + (lv?.let { " " + String.format(Locale.US, "%.1f", it) } ?: "")
                it.nativeCanvas.drawText(txt, 8f, rsiTop + 11f * d, rsiPaint)
            }
        }

        // trend lines
        for (ln in lines) {
            val x1 = xOfF(ln.x1)
            val y1 = yOf(ln.p1)
            val x2 = xOfF(ln.x2)
            val y2 = yOf(ln.p2)
            drawLine(DrawC, Offset(x1, y1), Offset(x2, y2), 1.6f)
            drawCircle(DrawC, 3f, Offset(x1, y1))
            drawCircle(DrawC, 3f, Offset(x2, y2))
        }
        preview?.let { (s, e) ->
            drawLine(DrawC.copy(alpha = 0.6f), s, e, 1.4f)
        }

        // last price line + right tag: price + countdown to candle close
        val lc = list.last()
        val ly = yOf(lc.close)
        drawLine(
            AxisC, Offset(0f, ly), Offset(widthF, ly), 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        )
        drawIntoCanvas {
            val txt = fmtPrice(lc.close)
            val remain = ((lc.openTime + data.intervalSec * 1000L - System.currentTimeMillis()) / 1000L)
                .coerceAtLeast(0L)
            val cnt = fmtCountdown(remain)
            val pw = tagPricePaint.measureText(txt)
            val cw = tagCntPaint.measureText(cnt)
            val tw = maxOf(pw, cw)
            val tagH = 30f * d
            it.nativeCanvas.drawRect(widthF - tw - 12f, ly - tagH / 2, widthF, ly + tagH / 2, tagBgPaint)
            it.nativeCanvas.drawText(txt, widthF - pw - 6f, ly - 2f * d, tagPricePaint)
            it.nativeCanvas.drawText(cnt, widthF - cw - 6f, ly + 10f * d, tagCntPaint)
        }

        // time labels
        val step = (70f / pxPer).toInt().coerceAtLeast(1)
        var i0 = first / step * step
        if (i0 < first) i0 += step
        for (i in i0..lastI step step) {
            val txt = tf.format(Date(list[i].openTime))
            val tw = labelPaint.measureText(txt)
            val xx = xOf(i).coerceIn(tw / 2 + 2f, widthF - tw / 2 - 2f)
            drawIntoCanvas { it.nativeCanvas.drawText(txt, xx - tw / 2, h - 6f * d, labelPaint) }
        }

        // crosshair
        cross?.let { cp ->
            drawLine(CrossC.copy(alpha = 0.7f), Offset(cp.x, priceTop), Offset(cp.x, crossBottom), 1f)
            drawLine(CrossC.copy(alpha = 0.7f), Offset(0f, cp.y), Offset(widthF, cp.y), 1f)
            val idx = (left + cp.x / pxPer).toInt().coerceIn(0, list.size - 1)
            val yy = cp.y.coerceIn(priceTop, priceBottom)
            val hv = loP + (priceBottom - yy) / priceH * (hiP - loP)
            drawIntoCanvas {
                val pt = fmtPrice(hv)
                val tw = labelPaint.measureText(pt)
                it.nativeCanvas.drawRect(widthF - tw - 12f, yy - 10f * d, widthF, yy + 10f * d, tagBgPaint)
                it.nativeCanvas.drawText(pt, widthF - tw - 6f, yy + 4f * d, tagTextPaint)

                val tt = tf.format(Date(list[idx].openTime))
                val ttw = labelPaint.measureText(tt)
                it.nativeCanvas.drawRect(cp.x - ttw / 2 - 8f, h - 18f * d, cp.x + ttw / 2 + 8f, h.toFloat(), tagBgPaint)
                it.nativeCanvas.drawText(tt, cp.x - ttw / 2, h - 6f * d, tagTextPaint)
            }
        }
    }
}

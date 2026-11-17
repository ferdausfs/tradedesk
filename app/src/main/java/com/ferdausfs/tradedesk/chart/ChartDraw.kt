package com.ferdausfs.tradedesk.chart

import android.graphics.Paint
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChartUi(
    val candles: List<Candle>,
    val ema: List<Double?> = emptyList(),
    val bbUpper: List<Double?> = emptyList(),
    val bbLower: List<Double?> = emptyList(),
    val daily: Boolean = false
)

private val GridC = Color(0xFF1E232B)
private val AxisC = Color(0xFF787B86)
private val CrossC = Color(0xFF9AA0AA)
private val UpC = Color(0xFF26A69A)
private val DownC = Color(0xFFEF5350)
private val EmaC = Color(0xFFF5A623)
private val BbC = Color(0xFF4A90E2)
private val TagBg = Color(0xFF2A2E39)
private val TagText = Color(0xFFE8EAED)

fun fmtPrice(v: Double): String = when {
    v >= 1000 -> String.format(Locale.US, "%.2f", v)
    v >= 1 -> String.format(Locale.US, "%.4f", v)
    else -> String.format(Locale.US, "%.6f", v)
}

@Composable
fun CandleChart(data: ChartUi, modifier: Modifier = Modifier) {

    val density = LocalDensity.current
    val d = density.density
    val minPx = with(density) { 2.dp.toPx() }
    val maxPx = with(density) { 40.dp.toPx() }
    val startPx = with(density) { 8.dp.toPx() }

    var pxPer by remember { mutableFloatStateOf(startPx) }
    var rightIdx by remember { mutableFloatStateOf(0f) }
    var follow by remember { mutableStateOf(true) }
    var cross by remember { mutableStateOf<Offset?>(null) }
    var w by remember { mutableIntStateOf(0) }
    var h by remember { mutableIntStateOf(0) }

    val labelPaint = remember {
        Paint().apply {
            color = AxisC.toArgb()
            textSize = 10f * d
            isAntiAlias = true
        }
    }
    val tagBgPaint = remember {
        Paint().apply { color = TagBg.toArgb() }
    }
    val tagTextPaint = remember {
        Paint().apply {
            color = TagText.toArgb()
            textSize = 10f * d
            isAntiAlias = true
        }
    }
    val tf = remember(data.daily) {
        if (data.daily) SimpleDateFormat("MMM d", Locale.US)
        else SimpleDateFormat("HH:mm", Locale.US)
    }

    Canvas(
        modifier
            .onSizeChanged { w = it.width; h = it.height }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    cross = down.position
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
    ) {
        val list = data.candles
        if (list.isEmpty() || w == 0 || h == 0) return@Canvas

        val volH = h * 0.14f
        val axisB = 18f * d
        val priceTop = 6f * d
        val priceH = (h - volH - axisB - priceTop - 4f * d).coerceAtLeast(10f)
        val priceBottom = priceTop + priceH
        val volBase = priceBottom + 4f * d + volH
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

        fun xOf(i: Int): Float = ((i + 0.5f - left) * pxPer)

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
            val col = if (c.close >= c.open) UpC else DownC
            val vh = if (maxVol > 0) (c.volume / maxVol * (volH - 2f)).toFloat() else 0f
            drawRect(
                col.copy(alpha = 0.55f),
                Offset(xOf(i) - bodyW / 2, volBase - vh),
                Size(bodyW, vh)
            )
        }

        // candles
        for (i in first..lastI) {
            val c = list[i]
            val col = if (c.close >= c.open) UpC else DownC
            val xx = xOf(i)
            val yH = yOf(c.high)
            val yL = yOf(c.low)
            val yO = yOf(c.open)
            val yC = yOf(c.close)
            val top = minOf(yO, yC)
            val bot = maxOf(yO, yC)
            drawLine(col, Offset(xx, yH), Offset(xx, maxOf(yL, top)), 1.2f)
            if (bot - top < 1.2f) {
                drawRect(col, Offset(xx - bodyW / 2, top), Size(bodyW, 1.5f))
            } else {
                drawRect(col, Offset(xx - bodyW / 2, top), Size(bodyW, bot - top))
            }
        }

        // overlay lines
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

        // last price line + right tag
        val lc = list.last()
        val ly = yOf(lc.close)
        val lcol = if (lc.close >= lc.open) UpC else DownC
        drawLine(
            lcol, Offset(0f, ly), Offset(widthF, ly), 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        )
        drawIntoCanvas {
            val txt = fmtPrice(lc.close)
            val tw = labelPaint.measureText(txt)
            it.nativeCanvas.drawRect(widthF - tw - 12f, ly - 10f * d, widthF, ly + 10f * d, tagBgPaint)
            it.nativeCanvas.drawText(txt, widthF - tw - 6f, ly + 4f * d, tagTextPaint)
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
            drawLine(CrossC.copy(alpha = 0.7f), Offset(cp.x, priceTop), Offset(cp.x, volBase), 1f)
            drawLine(CrossC.copy(alpha = 0.7f), Offset(0f, cp.y), Offset(widthF, cp.y), 1f)
            val idx = (left + cp.x / pxPer).toInt().coerceIn(0, list.size - 1)
            val hc = list[idx]
            val yy = cp.y.coerceIn(priceTop, priceBottom)
            val hv = loP + (priceBottom - yy) / priceH * (hiP - loP)
            drawIntoCanvas {
                val pt = fmtPrice(hv)
                val tw = labelPaint.measureText(pt)
                it.nativeCanvas.drawRect(widthF - tw - 12f, yy - 10f * d, widthF, yy + 10f * d, tagBgPaint)
                it.nativeCanvas.drawText(pt, widthF - tw - 6f, yy + 4f * d, tagTextPaint)

                val tt = tf.format(Date(hc.openTime))
                val ttw = labelPaint.measureText(tt)
                it.nativeCanvas.drawRect(cp.x - ttw / 2 - 8f, h - 18f * d, cp.x + ttw / 2 + 8f, h, tagBgPaint)
                it.nativeCanvas.drawText(tt, cp.x - ttw / 2, h - 6f * d, tagTextPaint)
            }
        }
    }
}

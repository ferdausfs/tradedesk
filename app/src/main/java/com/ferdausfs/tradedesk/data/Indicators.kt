package com.ferdausfs.tradedesk.data

import kotlin.math.sqrt

fun sma(v: List<Double>, p: Int): List<Double?> {
    val r = arrayOfNulls<Double>(v.size)
    if (p <= 0) return r.toList()
    var sum = 0.0
    for (i in v.indices) {
        sum += v[i]
        if (i >= p) sum -= v[i - p]
        if (i >= p - 1) r[i] = sum / p
    }
    return r.toList()
}

fun ema(v: List<Double>, p: Int): List<Double?> {
    val r = arrayOfNulls<Double>(v.size)
    if (v.isEmpty() || p <= 0) return r.toList()
    val k = 2.0 / (p + 1)
    var prev = Double.NaN
    for (i in v.indices) {
        prev = if (prev.isNaN()) {
            if (i >= p - 1) {
                var s = 0.0
                for (j in 0..i) s += v[j]
                s / p
            } else Double.NaN
        } else {
            v[i] * k + prev * (1 - k)
        }
        r[i] = if (prev.isNaN()) null else prev
    }
    return r.toList()
}

fun bollinger(v: List<Double>, p: Int = 20, m: Double = 2.0): Triple<List<Double?>, List<Double?>, List<Double?>> {
    val up = arrayOfNulls<Double>(v.size)
    val low = arrayOfNulls<Double>(v.size)
    val mid = sma(v, p)
    for (i in p - 1 until v.size) {
        val m0 = mid[i] ?: continue
        var s = 0.0
        for (j in i - p + 1..i) {
            val d = v[j] - m0
            s += d * d
        }
        val sd = sqrt(s / p)
        up[i] = m0 + m * sd
        low[i] = m0 - m * sd
    }
    return Triple(up.toList(), mid, low.toList())
}

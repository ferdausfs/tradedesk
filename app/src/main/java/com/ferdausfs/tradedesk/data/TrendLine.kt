package com.ferdausfs.tradedesk.data

import kotlinx.serialization.Serializable

@Serializable
data class TrendLine(val x1: Float, val p1: Double, val x2: Float, val p2: Double)

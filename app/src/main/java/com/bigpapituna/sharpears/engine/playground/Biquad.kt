package com.bigpapituna.sharpears.engine.playground

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A second-order filter section (RBJ cookbook), low- or high-pass at [hz], Butterworth. */
internal class Biquad(hz: Double, rate: Int, high: Boolean) {
    private val b0: Double; private val b1: Double; private val b2: Double; private val a1: Double; private val a2: Double
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
    init {
        val w = 2 * PI * hz / rate
        val alpha = sin(w) / (2 * sqrt(0.5))
        val cw = cos(w); val a0 = 1 + alpha
        val k = if (high) (1 + cw) / 2 else (1 - cw) / 2
        b0 = k / a0; b1 = (if (high) -2 * k else 2 * k) / a0; b2 = k / a0
        a1 = -2 * cw / a0; a2 = (1 - alpha) / a0
    }
    fun f(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }
    fun reset() { x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0 }
}

package com.androidscreentuner.extreme

import kotlin.math.ln
import kotlin.math.pow

object ColorMath {

    data class RGB(val r: Float, val g: Float, val b: Float)

    fun kelvinToRGB(kelvin: Int): RGB {
        val temp = kelvin / 100.0

        val r = if (temp <= 66) {
            1.0f
        } else {
            (1.2929 * (temp - 60).pow(-0.1332)).coerceIn(0.0, 1.0).toFloat()
        }

        val g = if (temp <= 66) {
            (0.3901 * ln(temp) - 0.6318).coerceIn(0.0, 1.0).toFloat()
        } else {
            (1.1299 * (temp - 60).pow(-0.0755)).coerceIn(0.0, 1.0).toFloat()
        }

        val b = when {
            temp >= 66 -> 1.0f
            temp <= 19 -> 0.0f
            else -> (0.5432 * ln(temp - 10) - 1.1962).coerceIn(0.0, 1.0).toFloat()
        }

        return RGB(r, g, b)
    }

    fun buildMatrix(kelvin: Int, dimFactor: Float): FloatArray {
        val rgb = kelvinToRGB(kelvin)
        val safeDim = dimFactor.coerceIn(0.05f, 1.0f)

        var r = safeDim * rgb.r
        var g = safeDim * rgb.g
        var b = safeDim * rgb.b

        val maxChannel = maxOf(r, g, b)
        if (maxChannel < 0.05f) {
            if (maxChannel > 0f) {
                val scale = 0.05f / maxChannel
                r *= scale
                g *= scale
                b *= scale
            } else {
                r = 0.05f
                g = 0.05f
                b = 0.05f
            }
        }

        return floatArrayOf(
            r, 0f, 0f, 0f,
            0f, g, 0f, 0f,
            0f, 0f, b, 0f,
            0f, 0f, 0f, 1f
        )
    }
}

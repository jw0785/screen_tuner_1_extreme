package com.androidscreentuner.extreme

import kotlin.math.pow

object ColorMath {

    data class RGB(val r: Float, val g: Float, val b: Float)

    val SRGB = doubleArrayOf(
         3.2404542, -1.5371385, -0.4985314,
        -0.9692660,  1.8760108,  0.0415560,
         0.0556434, -0.2040259,  1.0572252
    )

    val DISPLAY_P3 = doubleArrayOf(
         2.4934969, -0.9313836, -0.4027108,
        -0.8294890,  1.7626641,  0.0236247,
         0.0358458, -0.0761724,  0.9568845
    )

    fun kelvinToRGB(kelvin: Int, xyzToRgb: DoubleArray = SRGB): RGB {
        val t = kelvin.toDouble().coerceIn(1667.0, 25000.0)

        val x = if (t <= 4000) {
            -0.2661239e9 / (t * t * t) - 0.2343589e6 / (t * t) + 0.8776956e3 / t + 0.179910
        } else {
            -3.0258469e9 / (t * t * t) + 2.1070379e6 / (t * t) + 0.2226347e3 / t + 0.240390
        }

        val y = when {
            t <= 2222 -> -1.1063814 * x * x * x - 1.34811020 * x * x + 2.18555832 * x - 0.20219683
            t <= 4000 -> -0.9549476 * x * x * x - 1.37418593 * x * x + 2.09137015 * x - 0.16748867
            else -> 3.0817580 * x * x * x - 5.87338670 * x * x + 3.75112997 * x - 0.37001483
        }

        val bigX = x / y
        val bigZ = (1.0 - x - y) / y

        val m = xyzToRgb
        var rLin = m[0] * bigX + m[1] + m[2] * bigZ
        var gLin = m[3] * bigX + m[4] + m[5] * bigZ
        var bLin = m[6] * bigX + m[7] + m[8] * bigZ

        val maxLin = maxOf(rLin, gLin, bLin)
        if (maxLin > 0) {
            rLin /= maxLin
            gLin /= maxLin
            bLin /= maxLin
        }

        return RGB(
            rLin.coerceIn(0.0, 1.0).pow(1.0 / 2.2).toFloat(),
            gLin.coerceIn(0.0, 1.0).pow(1.0 / 2.2).toFloat(),
            bLin.coerceIn(0.0, 1.0).pow(1.0 / 2.2).toFloat()
        )
    }

    fun buildMatrix(kelvin: Int, dimFactor: Float, xyzToRgb: DoubleArray = SRGB): FloatArray {
        val rgb = kelvinToRGB(kelvin, xyzToRgb)
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

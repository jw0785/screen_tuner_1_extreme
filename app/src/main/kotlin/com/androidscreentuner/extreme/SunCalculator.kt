package com.androidscreentuner.extreme

import java.util.Calendar
import kotlin.math.*

object SunCalculator {

    data class SunTimes(val sunriseMinutes: Int, val sunsetMinutes: Int)

    fun calculate(latitude: Double, longitude: Double, calendar: Calendar): SunTimes {
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val tzOffsetHours = calendar.timeZone.getOffset(calendar.timeInMillis) / 3_600_000.0

        val declination = -23.45 * cos(Math.toRadians(360.0 / 365.0 * (dayOfYear + 10)))
        val latRad = Math.toRadians(latitude)
        val decRad = Math.toRadians(declination)

        val cosHA = -tan(latRad) * tan(decRad)

        if (cosHA < -1.0 || cosHA > 1.0) {
            val solarNoon = (720 - 4 * longitude + tzOffsetHours * 60).toInt()
            return SunTimes(wrap(solarNoon - 360), wrap(solarNoon + 360))
        }

        val haMinutes = Math.toDegrees(acos(cosHA)) * 4.0

        val b = Math.toRadians(360.0 / 365.0 * (dayOfYear - 81))
        val eot = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)

        val solarNoon = 720.0 - 4.0 * longitude - eot + tzOffsetHours * 60.0
        val sunrise = solarNoon - haMinutes
        val sunset = solarNoon + haMinutes

        return SunTimes(wrap(sunrise.toInt()), wrap(sunset.toInt()))
    }

    private fun wrap(m: Int): Int = ((m % 1440) + 1440) % 1440
}

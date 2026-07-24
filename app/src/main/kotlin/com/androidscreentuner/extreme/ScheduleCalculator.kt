package com.androidscreentuner.extreme

object ScheduleCalculator {

    enum class Segment { DAYTIME, SUNSET, BEDTIME }

    private const val SLEEP_DURATION = 480
    private const val TRANSITION_MIN = 60
    private const val TRANSITION_STEPS = 12

    @Volatile var currentSegment: Segment? = null
        private set

    private var wakeTime = 0
    private var sunsetTime = -1
    private var bedtimeStart = 0
    private var hasSunset = false

    fun updateSchedule(wakeMinutes: Int, sunsetMinutes: Int) {
        wakeTime = wakeMinutes
        bedtimeStart = wrap(wakeMinutes - SLEEP_DURATION)

        val wakeToSunset = forwardDist(wakeTime, sunsetMinutes)
        val wakeToBed = forwardDist(wakeTime, bedtimeStart)
        hasSunset = wakeToSunset > 0 && wakeToSunset < wakeToBed

        sunsetTime = if (hasSunset) sunsetMinutes else -1
    }

    fun tick(currentMinutes: Int, daytimeK: Int, sunsetK: Int, bedtimeK: Int): Int {
        val wakeRamp = wrap(wakeTime - TRANSITION_MIN)
        val bedtimeRamp = wrap(bedtimeStart - TRANSITION_MIN)

        if (hasSunset) {
            val sunsetRamp = wrap(sunsetTime - TRANSITION_MIN)

            if (inSegment(currentMinutes, wakeRamp, sunsetRamp)) {
                currentSegment = Segment.DAYTIME
                return if (inSegment(currentMinutes, wakeRamp, wakeTime))
                    step(bedtimeK, daytimeK, progress(currentMinutes, wakeRamp, wakeTime))
                else daytimeK
            }
            if (inSegment(currentMinutes, sunsetRamp, bedtimeRamp)) {
                currentSegment = Segment.SUNSET
                return if (inSegment(currentMinutes, sunsetRamp, sunsetTime))
                    step(daytimeK, sunsetK, progress(currentMinutes, sunsetRamp, sunsetTime))
                else sunsetK
            }
            currentSegment = Segment.BEDTIME
            return if (inSegment(currentMinutes, bedtimeRamp, bedtimeStart))
                step(sunsetK, bedtimeK, progress(currentMinutes, bedtimeRamp, bedtimeStart))
            else bedtimeK
        }

        if (inSegment(currentMinutes, wakeRamp, bedtimeRamp)) {
            currentSegment = Segment.DAYTIME
            return if (inSegment(currentMinutes, wakeRamp, wakeTime))
                step(bedtimeK, daytimeK, progress(currentMinutes, wakeRamp, wakeTime))
            else daytimeK
        }
        currentSegment = Segment.BEDTIME
        return if (inSegment(currentMinutes, bedtimeRamp, bedtimeStart))
            step(daytimeK, bedtimeK, progress(currentMinutes, bedtimeRamp, bedtimeStart))
        else bedtimeK
    }

    fun clear() {
        currentSegment = null
    }

    private fun step(from: Int, to: Int, t: Float): Int {
        val s = (t * TRANSITION_STEPS).toInt().toFloat() / TRANSITION_STEPS
        return (from + s * (to - from)).toInt()
    }

    private fun forwardDist(from: Int, to: Int): Int = ((to - from) % 1440 + 1440) % 1440

    private fun wrap(m: Int): Int = ((m % 1440) + 1440) % 1440

    private fun inSegment(current: Int, start: Int, end: Int): Boolean =
        if (start <= end) current in start until end else current >= start || current < end

    private fun progress(current: Int, start: Int, end: Int): Float {
        val len = if (end > start) end - start else 1440 - start + end
        val elapsed = if (current >= start) current - start else 1440 - start + current
        return (elapsed.toFloat() / len).coerceIn(0f, 1f)
    }
}

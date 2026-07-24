package com.androidscreentuner.extreme

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREVIEW_DURATION_MS = 3000L
    }

    private lateinit var prefs: SharedPreferences

    private lateinit var warningBanner: View
    private lateinit var masterToggle: SwitchMaterial
    private lateinit var brightnessSlider: SeekBar
    private lateinit var brightnessValue: TextView
    private lateinit var daytimeSlider: SeekBar
    private lateinit var daytimeValue: TextView
    private lateinit var swatchDaytime: View
    private lateinit var rowDaytime: View
    private lateinit var sunsetSlider: SeekBar
    private lateinit var sunsetValue: TextView
    private lateinit var swatchSunset: View
    private lateinit var rowSunset: View
    private lateinit var bedtimeSlider: SeekBar
    private lateinit var bedtimeValue: TextView
    private lateinit var swatchBedtime: View
    private lateinit var rowBedtime: View
    private lateinit var wakeTimeButton: Button
    private lateinit var locationToggle: SwitchMaterial

    private var conflictDialog: AlertDialog? = null
    private val previewHandler = Handler(Looper.getMainLooper())
    private var previewRunnable: Runnable? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fetchLocation()
        } else {
            locationToggle.isChecked = false
            prefs.edit().putBoolean("use_location", false).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("screen_tuner_prefs", Context.MODE_PRIVATE)
        bindViews()
        setupListeners()
        loadPreferences()
    }

    override fun onResume() {
        super.onResume()
        checkConflictingServices()
        updateActiveSegment()
    }

    private fun bindViews() {
        warningBanner = findViewById(R.id.warningBanner)
        masterToggle = findViewById(R.id.masterToggle)
        brightnessSlider = findViewById(R.id.brightnessSlider)
        brightnessValue = findViewById(R.id.brightnessValue)
        daytimeSlider = findViewById(R.id.daytimeSlider)
        daytimeValue = findViewById(R.id.daytimeValue)
        swatchDaytime = findViewById(R.id.swatchDaytime)
        rowDaytime = findViewById(R.id.rowDaytime)
        sunsetSlider = findViewById(R.id.sunsetSlider)
        sunsetValue = findViewById(R.id.sunsetValue)
        swatchSunset = findViewById(R.id.swatchSunset)
        rowSunset = findViewById(R.id.rowSunset)
        bedtimeSlider = findViewById(R.id.bedtimeSlider)
        bedtimeValue = findViewById(R.id.bedtimeValue)
        swatchBedtime = findViewById(R.id.swatchBedtime)
        rowBedtime = findViewById(R.id.rowBedtime)
        wakeTimeButton = findViewById(R.id.wakeTimeButton)
        locationToggle = findViewById(R.id.locationToggle)

        findViewById<Button>(R.id.btnOpenDisplaySettings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        }
    }

    private fun setupListeners() {
        masterToggle.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean("master_enabled", enabled).apply()
            if (enabled) {
                startColorService()
                previewHandler.postDelayed({ updateActiveSegment() }, 500)
            } else {
                stopColorService()
                updateActiveSegment()
            }
        }

        brightnessSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val percent = progress.coerceAtLeast(5)
                brightnessValue.text = "$percent%"
                if (!fromUser) return
                prefs.edit().putFloat("dim_factor", percent / 100f).apply()
                triggerRecalculate()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        setupTempSlider(daytimeSlider, daytimeValue, swatchDaytime, 1800, "daytime_k")
        setupTempSlider(sunsetSlider, sunsetValue, swatchSunset, 1800, "sunset_k")
        setupTempSlider(bedtimeSlider, bedtimeValue, swatchBedtime, 1800, "bedtime_k")

        wakeTimeButton.setOnClickListener {
            val h = prefs.getInt("wake_hour", 7)
            val m = prefs.getInt("wake_minute", 0)
            TimePickerDialog(this, { _, hour, minute ->
                prefs.edit().putInt("wake_hour", hour).putInt("wake_minute", minute).apply()
                wakeTimeButton.text = String.format("%02d:%02d", hour, minute)
                triggerRecalculate()
            }, h, m, true).show()
        }

        locationToggle.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean("use_location", enabled).apply()
            if (enabled) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    fetchLocation()
                } else {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
        }
    }

    private fun setupTempSlider(
        slider: SeekBar, label: TextView, swatch: View, baseK: Int, prefKey: String
    ) {
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val kelvin = baseK + progress * 100
                label.text = "${kelvin}K"
                updateSwatch(swatch, kelvin)
                if (!fromUser) return
                prefs.edit().putInt(prefKey, kelvin).apply()
                applyPreview(kelvin)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                ColorService.previewUntilMs = Long.MAX_VALUE
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                schedulePreviewEnd()
            }
        })
    }

    private fun loadPreferences() {
        masterToggle.isChecked = prefs.getBoolean("master_enabled", false)

        val dimPercent = (prefs.getFloat("dim_factor", 0.5f) * 100).toInt()
        brightnessSlider.progress = dimPercent
        brightnessValue.text = "$dimPercent%"

        val daytimeK = prefs.getInt("daytime_k", 5000)
        daytimeSlider.progress = (daytimeK - 1800) / 100
        daytimeValue.text = "${daytimeK}K"
        updateSwatch(swatchDaytime, daytimeK)

        val sunsetK = prefs.getInt("sunset_k", 4200)
        sunsetSlider.progress = (sunsetK - 1800) / 100
        sunsetValue.text = "${sunsetK}K"
        updateSwatch(swatchSunset, sunsetK)

        val bedtimeK = prefs.getInt("bedtime_k", 3800)
        bedtimeSlider.progress = (bedtimeK - 1800) / 100
        bedtimeValue.text = "${bedtimeK}K"
        updateSwatch(swatchBedtime, bedtimeK)

        val wakeH = prefs.getInt("wake_hour", 7)
        val wakeM = prefs.getInt("wake_minute", 0)
        wakeTimeButton.text = String.format("%02d:%02d", wakeH, wakeM)

        locationToggle.isChecked = prefs.getBoolean("use_location", false)
    }

    private fun checkConflictingServices() {
        val nightLight = try {
            Settings.Secure.getInt(contentResolver, "night_display_activated", 0)
        } catch (_: SecurityException) { 0 }
        val extraDim = try {
            Settings.Secure.getInt(contentResolver, "reduce_bright_colors_activated", 0)
        } catch (_: SecurityException) { 0 }
        val conflict = nightLight == 1 || extraDim == 1

        warningBanner.visibility = if (conflict) View.VISIBLE else View.GONE

        if (conflict && conflictDialog?.isShowing != true) {
            conflictDialog = AlertDialog.Builder(this)
                .setMessage(R.string.warning_conflict)
                .setPositiveButton(R.string.open_display_settings) { _, _ ->
                    startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else if (!conflict) {
            conflictDialog?.dismiss()
        }
    }

    private fun updateSwatch(swatch: View, kelvin: Int) {
        val rgb = ColorMath.kelvinToRGB(kelvin)
        val color = Color.rgb(
            (rgb.r * 255).toInt(),
            (rgb.g * 255).toInt(),
            (rgb.b * 255).toInt()
        )
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 4f * resources.displayMetrics.density
            setColor(color)
        }
        swatch.background = drawable
    }

    private fun updateActiveSegment() {
        val rows = listOf(rowDaytime, rowSunset, rowBedtime)
        val segment = ScheduleCalculator.currentSegment

        if (segment == null) {
            rows.forEach { it.setBackgroundColor(Color.TRANSPARENT) }
            return
        }

        val activeRow = when (segment) {
            ScheduleCalculator.Segment.DAYTIME -> rowDaytime
            ScheduleCalculator.Segment.SUNSET -> rowSunset
            ScheduleCalculator.Segment.BEDTIME -> rowBedtime
        }

        val highlight = resources.getColor(R.color.active_segment_bg, theme)
        rows.forEach { it.setBackgroundColor(if (it == activeRow) highlight else Color.TRANSPARENT) }
    }

    private fun applyPreview(kelvin: Int) {
        if (!prefs.getBoolean("master_enabled", false)) return
        Thread {
            val dimFactor = prefs.getFloat("dim_factor", 0.5f)
            val matrix = ColorMath.buildMatrix(kelvin, dimFactor)
            SurfaceFlingerManager.applyMatrix(matrix)
        }.start()
    }

    private fun schedulePreviewEnd() {
        previewRunnable?.let { previewHandler.removeCallbacks(it) }
        previewRunnable = Runnable {
            ColorService.previewUntilMs = 0
            triggerRecalculate()
        }
        previewHandler.postDelayed(previewRunnable!!, PREVIEW_DURATION_MS)
    }

    private fun startColorService() {
        Thread {
            if (!SurfaceFlingerManager.openRootShell()) {
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setMessage(R.string.error_no_root)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    masterToggle.isChecked = false
                }
                return@Thread
            }

            val cached = prefs.getInt("sf_transaction_code", -1)
            if (!SurfaceFlingerManager.probeWithCached(cached)) {
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setMessage(R.string.error_no_surfaceflinger)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    masterToggle.isChecked = false
                }
                return@Thread
            }

            prefs.edit()
                .putInt("sf_transaction_code", SurfaceFlingerManager.transactionCode)
                .apply()

            runOnUiThread {
                startForegroundService(Intent(this, ColorService::class.java))
            }
        }.start()
    }

    private fun stopColorService() {
        stopService(Intent(this, ColorService::class.java))
    }

    private fun triggerRecalculate() {
        if (!prefs.getBoolean("master_enabled", false)) return
        startForegroundService(Intent(this, ColorService::class.java))
    }

    private fun fetchLocation() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc != null) {
                prefs.edit()
                    .putFloat("cached_latitude", loc.latitude.toFloat())
                    .putFloat("cached_longitude", loc.longitude.toFloat())
                    .apply()
            }
        } catch (_: SecurityException) {
            // Permission not granted
        }
    }
}

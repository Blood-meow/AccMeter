package com.example.accmeter

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlin.math.sqrt

class AccelerationFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var sensor: Sensor? = null
    private var useLinearAcceleration = false

    private var speedX = 0f
    private var speedY = 0f
    private var speedZ = 0f
    private var lastTimestamp = 0L
    private var isFirstEvent = true

    private val alpha = 0.9f
    private var gravity = FloatArray(3)

    private val decay = 0.98f
    private val staticThreshold = 0.15f

    private var latestLinAccel = FloatArray(3)
    private var latestTimestampForChart = 0L
    private var isKmh = false
    private var isSmooth = false
    private val speedHistory = ArrayList<Pair<Long, Float>>()
    private var displaySpeedMs = 0f

    private lateinit var tvSpeed: TextView
    private lateinit var switchUnit: SwitchMaterial
    private lateinit var switchSmooth: SwitchMaterial
    private lateinit var btnClear: MaterialButton
    private lateinit var radioGroupRefresh: RadioGroup

    private lateinit var chartTotalSpeed: RealTimeChart
    private lateinit var chartAccel: RealTimeChart
    private lateinit var chartSpeed: RealTimeChart

    private val refreshHandler = Handler(Looper.getMainLooper())
    private var refreshInterval = 33L
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (latestTimestampForChart > 0) {
                val totalSpeed = sqrt(speedX * speedX + speedY * speedY + speedZ * speedZ)
                speedHistory.add(Pair(latestTimestampForChart, totalSpeed))
                val cutoff = latestTimestampForChart - 3_000_000_000L
                speedHistory.removeAll { it.first < cutoff }

                val currentDisplaySpeedMs = computeDisplaySpeedMs(totalSpeed)
                displaySpeedMs = currentDisplaySpeedMs

                chartTotalSpeed.addData(currentDisplaySpeedMs, currentDisplaySpeedMs, currentDisplaySpeedMs, latestTimestampForChart)
                chartAccel.addData(latestLinAccel[0], latestLinAccel[1], latestLinAccel[2], latestTimestampForChart)
                chartSpeed.addData(speedX, speedY, speedZ, latestTimestampForChart)

                chartTotalSpeed.invalidate()
                chartAccel.invalidate()
                chartSpeed.invalidate()
                updateSpeedTextView()
            }
            refreshHandler.postDelayed(this, refreshInterval)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_acceleration, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // 控件初始化
        tvSpeed = view.findViewById(R.id.tvSpeed)
        switchUnit = view.findViewById(R.id.switchUnit)
        switchSmooth = view.findViewById(R.id.switchSmooth)
        btnClear = view.findViewById(R.id.btnClear)
        radioGroupRefresh = view.findViewById(R.id.radioGroupRefresh)

        chartTotalSpeed = view.findViewById<RealTimeChart>(R.id.chartTotalSpeed).apply {
            title = "合速度"
            unit = "m/s"
            singleLine = true
        }
        chartAccel = view.findViewById<RealTimeChart>(R.id.chartAccel).apply {
            title = "线性加速度"
            unit = "m/s²"
        }
        chartSpeed = view.findViewById<RealTimeChart>(R.id.chartSpeed).apply {
            title = "分速度"
            unit = "m/s"
        }

        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.also {
            useLinearAcceleration = true
        } ?: run {
            useLinearAcceleration = false
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }

        switchUnit.setOnCheckedChangeListener { _, isChecked ->
            isKmh = isChecked
            updateSpeedTextView()
        }
        switchSmooth.setOnCheckedChangeListener { _, isChecked ->
            isSmooth = isChecked
            updateSpeedTextView()
        }
        radioGroupRefresh.setOnCheckedChangeListener { _, checkedId ->
            refreshInterval = when (checkedId) {
                R.id.rbLow -> 100L
                R.id.rbMedium -> 33L
                R.id.rbHigh -> 16L
                else -> 33L
            }
            restartRefreshTimer()
        }
        btnClear.setOnClickListener {
            speedX = 0f; speedY = 0f; speedZ = 0f
            speedHistory.clear()
            displaySpeedMs = 0f
            updateSpeedTextView()
            chartTotalSpeed.clearData()
            chartAccel.clearData()
            chartSpeed.clearData()
        }

        // ========== 折叠设置区域 ==========
        val headerSettings = view.findViewById<View>(R.id.headerSettings)
        val settingsContent = view.findViewById<View>(R.id.settingsContent)
        val tvExpandIndicator = view.findViewById<TextView>(R.id.tvExpandIndicator)

        headerSettings.setOnClickListener {
            if (settingsContent.visibility == View.VISIBLE) {
                settingsContent.visibility = View.GONE
                tvExpandIndicator.text = "▶"
            } else {
                settingsContent.visibility = View.VISIBLE
                tvExpandIndicator.text = "▼"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        refreshHandler.postDelayed(refreshRunnable, refreshInterval)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val currentTime = event.timestamp
        if (isFirstEvent) {
            lastTimestamp = currentTime
            isFirstEvent = false
            if (!useLinearAcceleration) {
                gravity[0] = event.values[0]
                gravity[1] = event.values[1]
                gravity[2] = event.values[2]
            }
            return
        }
        val dt = (currentTime - lastTimestamp) / 1_000_000_000f
        lastTimestamp = currentTime

        val linAccel = if (useLinearAcceleration) {
            event.values.clone()
        } else {
            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]
            floatArrayOf(
                event.values[0] - gravity[0],
                event.values[1] - gravity[1],
                event.values[2] - gravity[2]
            )
        }
        latestLinAccel = linAccel
        latestTimestampForChart = currentTime

        val accelMag = sqrt(linAccel[0] * linAccel[0] + linAccel[1] * linAccel[1] + linAccel[2] * linAccel[2])
        if (accelMag < staticThreshold) {
            speedX *= 0.2f; speedY *= 0.2f; speedZ *= 0.2f
        } else {
            speedX += linAccel[0] * dt
            speedY += linAccel[1] * dt
            speedZ += linAccel[2] * dt
            speedX *= decay; speedY *= decay; speedZ *= decay
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun computeDisplaySpeedMs(instantSpeed: Float): Float {
        if (!isSmooth || speedHistory.isEmpty()) return instantSpeed
        val now = speedHistory.last().first
        val cutoff = now - 3_000_000_000L
        var sum = 0f
        var count = 0
        for ((ts, sp) in speedHistory) {
            if (ts >= cutoff) { sum += sp; count++ }
        }
        return if (count > 0) sum / count else instantSpeed
    }

    private fun updateSpeedTextView() {
        val converted = if (isKmh) displaySpeedMs * 3.6f else displaySpeedMs
        val unit = if (isKmh) "km/h" else "m/s"
        tvSpeed.text = String.format("%.2f %s", converted, unit)
    }

    private fun restartRefreshTimer() {
        refreshHandler.removeCallbacks(refreshRunnable)
        if (latestTimestampForChart > 0) {
            refreshHandler.postDelayed(refreshRunnable, refreshInterval)
        }
    }
}

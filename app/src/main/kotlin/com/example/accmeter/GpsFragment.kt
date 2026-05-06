package com.example.accmeter

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class GpsFragment : Fragment() {

    private lateinit var locationManager: LocationManager
    private var gpsSpeed = 0f
    private var isKmh = false
    private var isSmooth = false
    private var displaySpeedMs = 0f
    private val speedHistory = ArrayList<Pair<Long, Float>>()
    private var latestTimestampForChart = 0L
    private var isListening = false

    // UI 控件
    private lateinit var tvGpsStatus: TextView
    private lateinit var tvGpsSpeed: TextView
    private lateinit var btnStartStop: MaterialButton
    private lateinit var switchUnit: SwitchMaterial
    private lateinit var switchSmooth: SwitchMaterial
    private lateinit var radioGroupRefresh: RadioGroup
    private lateinit var btnClear: MaterialButton
    private lateinit var chartGpsSpeed: RealTimeChart

    // 定时刷新
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var refreshInterval = 33L
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (isListening && latestTimestampForChart > 0) {
                speedHistory.add(Pair(latestTimestampForChart, gpsSpeed))
                val cutoff = latestTimestampForChart - 3_000_000_000L
                speedHistory.removeAll { it.first < cutoff }

                displaySpeedMs = if (isSmooth && speedHistory.isNotEmpty()) {
                    val sum = speedHistory.filter { it.first >= cutoff }.sumOf { it.second.toDouble() }
                    val count = speedHistory.count { it.first >= cutoff }
                    if (count > 0) (sum / count).toFloat() else gpsSpeed
                } else gpsSpeed

                chartGpsSpeed.addData(displaySpeedMs, displaySpeedMs, displaySpeedMs, latestTimestampForChart)
                chartGpsSpeed.invalidate()
                updateSpeedText()
            }
            refreshHandler.postDelayed(this, refreshInterval)
        }
    }

    // GPS 位置监听器
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            gpsSpeed = if (location.hasSpeed()) location.speed else 0f
            latestTimestampForChart = System.nanoTime()
            activity?.runOnUiThread {
                tvGpsStatus.text = "GPS 已连接"
            }
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {
            activity?.runOnUiThread { tvGpsStatus.text = "GPS 已开启" }
        }
        override fun onProviderDisabled(provider: String) {
            activity?.runOnUiThread {
                tvGpsStatus.text = "GPS 已关闭"
                gpsSpeed = 0f
                updateSpeedText()
            }
        }
    }

    // 权限请求启动器
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startListening()
            } else {
                Toast.makeText(requireContext(), "定位权限被拒绝，无法使用 GPS 测速", Toast.LENGTH_SHORT).show()
                tvGpsStatus.text = "权限被拒绝"
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_gps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // 控件初始化
        tvGpsStatus = view.findViewById(R.id.tvGpsStatus)
        tvGpsSpeed = view.findViewById(R.id.tvGpsSpeed)
        btnStartStop = view.findViewById(R.id.btnStartStopGps)
        switchUnit = view.findViewById(R.id.switchUnitGps)
        switchSmooth = view.findViewById(R.id.switchSmoothGps)
        radioGroupRefresh = view.findViewById(R.id.radioGroupRefreshGps)
        btnClear = view.findViewById(R.id.btnClearGps)
        chartGpsSpeed = view.findViewById<RealTimeChart>(R.id.chartGpsSpeed).apply {
            title = "GPS 速度"
            unit = "m/s"
            singleLine = true
        }

        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // 开启/关闭 GPS 按钮
        btnStartStop.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                if (hasLocationPermission()) {
                    startListening()
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
        }

        // 单位切换
        switchUnit.setOnCheckedChangeListener { _, isChecked ->
            isKmh = isChecked
            updateSpeedText()
        }

        // 平滑显示
        switchSmooth.setOnCheckedChangeListener { _, isChecked ->
            isSmooth = isChecked
            updateSpeedText()
        }

        // 刷新率
        radioGroupRefresh.setOnCheckedChangeListener { _, checkedId ->
            refreshInterval = when (checkedId) {
                R.id.rbLowGps -> 100L
                R.id.rbMediumGps -> 33L
                R.id.rbHighGps -> 16L
                else -> 33L
            }
            restartRefreshTimer()
        }

        // 清零
        btnClear.setOnClickListener {
            gpsSpeed = 0f
            speedHistory.clear()
            displaySpeedMs = 0f
            updateSpeedText()
            chartGpsSpeed.clearData()
            latestTimestampForChart = 0L
        }

        // ========== 折叠设置区域 ==========
        val headerSettingsGps = view.findViewById<View>(R.id.headerSettingsGps)
        val settingsContentGps = view.findViewById<View>(R.id.settingsContentGps)
        val tvExpandIndicatorGps = view.findViewById<TextView>(R.id.tvExpandIndicatorGps)

        headerSettingsGps.setOnClickListener {
            if (settingsContentGps.visibility == View.VISIBLE) {
                settingsContentGps.visibility = View.GONE
                tvExpandIndicatorGps.text = "▶"
            } else {
                settingsContentGps.visibility = View.VISIBLE
                tvExpandIndicatorGps.text = "▼"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshHandler.postDelayed(refreshRunnable, refreshInterval)
    }

    override fun onPause() {
        super.onPause()
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    // ========== 辅助方法 ==========

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun startListening() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000,
                0f,
                locationListener
            )
            isListening = true
            btnStartStop.text = "关闭 GPS 测速"
            tvGpsStatus.text = "请求 GPS..."
        } catch (e: SecurityException) {
            tvGpsStatus.text = "权限被拒"
        } catch (e: Exception) {
            tvGpsStatus.text = "GPS 不可用"
            Toast.makeText(context, "GPS 服务启动失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopListening() {
        locationManager.removeUpdates(locationListener)
        isListening = false
        btnStartStop.text = "开启 GPS 测速"
        tvGpsStatus.text = "已停止"
        gpsSpeed = 0f
        latestTimestampForChart = 0L
        updateSpeedText()
    }

    private fun updateSpeedText() {
        val converted = if (isKmh) displaySpeedMs * 3.6f else displaySpeedMs
        val unit = if (isKmh) "km/h" else "m/s"
        tvGpsSpeed.text = String.format("%.2f %s", converted, unit)
    }

    private fun restartRefreshTimer() {
        refreshHandler.removeCallbacks(refreshRunnable)
        if (latestTimestampForChart > 0) {
            refreshHandler.postDelayed(refreshRunnable, refreshInterval)
        }
    }
}

package com.example.accmeter

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class RealTimeChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dataPointsX = ArrayList<Float>()
    private val dataPointsY = ArrayList<Float>()
    private val dataPointsZ = ArrayList<Float>()
    private var maxDataPoints = 150
    private var latestTimestamp: Long? = null

    private var yMin = -1f
    private var yMax = 1f

    var singleLine: Boolean = false

    private val paintGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30FFFFFF"); strokeWidth = 1f
    }
    private val paintLineX = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5C5C"); strokeWidth = 4f; style = Paint.Style.STROKE
    }
    private val paintLineY = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FF66"); strokeWidth = 4f; style = Paint.Style.STROKE
    }
    private val paintLineZ = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF"); strokeWidth = 4f; style = Paint.Style.STROKE
    }
    private val paintLineSingle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF"); strokeWidth = 5f; style = Paint.Style.STROKE
        pathEffect = CornerPathEffect(10f)
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.LEFT
    }

    var title: String = ""
    var unit: String = ""

    fun setTimeWindow(seconds: Float) {}

    fun addData(x: Float, y: Float, z: Float, timestamp: Long) {
        latestTimestamp = timestamp
        dataPointsX.add(x)
        dataPointsY.add(y)
        dataPointsZ.add(z)
        while (dataPointsX.size > maxDataPoints) {
            dataPointsX.removeAt(0)
            dataPointsY.removeAt(0)
            dataPointsZ.removeAt(0)
        }
    }

    fun clearData() {
        dataPointsX.clear(); dataPointsY.clear(); dataPointsZ.clear()
        latestTimestamp = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        maxDataPoints = w / 6
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataPointsX.isEmpty()) {
            canvas.drawText("等待数据...", 20f, height / 2f, paintText)
            return
        }

        val w = width.toFloat(); val h = height.toFloat()
        val margin = 40f
        val graphLeft = margin + 40f
        val graphRight = w - margin
        val graphTop = margin + 40f
        val graphBottom = h - margin - 20f

        updateYRange()
        val ySpan = (yMax - yMin).coerceAtLeast(0.1f)

        canvas.drawColor(Color.parseColor("#1C1B1F"))
        canvas.drawText("$title ($unit)", margin, margin, paintText)

        val gridLines = 4
        for (i in 0..gridLines) {
            val y = graphTop + (graphBottom - graphTop) * i / gridLines
            canvas.drawLine(graphLeft, y, graphRight, y, paintGrid)
            val value = yMin + ySpan * (1 - i.toFloat() / gridLines)
            canvas.drawText(String.format("%.2f", value), margin, y + 8f, paintText)
        }

        val stepX = (graphRight - graphLeft) / (dataPointsX.size - 1).coerceAtLeast(1)

        if (singleLine) {
            drawPath(canvas, dataPointsX, graphLeft, graphTop, graphBottom, yMin, ySpan, stepX, paintLineSingle)
            canvas.drawLine(graphRight - 150, graphBottom + 10, graphRight - 120, graphBottom + 10, paintLineSingle)
            canvas.drawText("Speed", graphRight - 110, graphBottom + 20, paintText)
        } else {
            drawPath(canvas, dataPointsX, graphLeft, graphTop, graphBottom, yMin, ySpan, stepX, paintLineX)
            drawPath(canvas, dataPointsY, graphLeft, graphTop, graphBottom, yMin, ySpan, stepX, paintLineY)
            drawPath(canvas, dataPointsZ, graphLeft, graphTop, graphBottom, yMin, ySpan, stepX, paintLineZ)
            drawLegend(canvas, w, h)
        }
    }

    private fun updateYRange() {
        var maxVal = -Float.MAX_VALUE; var minVal = Float.MAX_VALUE
        for (i in 0 until dataPointsX.size) {
            maxVal = maxVal.coerceAtLeast(dataPointsX[i])
            maxVal = maxVal.coerceAtLeast(dataPointsY[i])
            maxVal = maxVal.coerceAtLeast(dataPointsZ[i])
            minVal = minVal.coerceAtMost(dataPointsX[i])
            minVal = minVal.coerceAtMost(dataPointsY[i])
            minVal = minVal.coerceAtMost(dataPointsZ[i])
        }
        if (maxVal == minVal) { maxVal += 0.5f; minVal -= 0.5f }
        yMin = minVal; yMax = maxVal
    }

    private fun drawPath(canvas: Canvas, data: ArrayList<Float>,
                         left: Float, top: Float, bottom: Float,
                         yMin: Float, ySpan: Float, stepX: Float,
                         paint: Paint) {
        if (data.size < 2) return
        val path = Path()
        for (i in data.indices) {
            val x = left + i * stepX
            val y = top + (bottom - top) * (1 - (data[i] - yMin) / ySpan)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawLegend(canvas: Canvas, w: Float, h: Float) {
        val legendX = w - 250f; val legendY = h - 60f
        canvas.drawLine(legendX, legendY, legendX + 30, legendY, paintLineX)
        canvas.drawText("X", legendX + 40, legendY + 8, paintText)
        canvas.drawLine(legendX + 90, legendY, legendX + 120, legendY, paintLineY)
        canvas.drawText("Y", legendX + 130, legendY + 8, paintText)
        canvas.drawLine(legendX + 180, legendY, legendX + 210, legendY, paintLineZ)
        canvas.drawText("Z", legendX + 220, legendY + 8, paintText)
    }
}

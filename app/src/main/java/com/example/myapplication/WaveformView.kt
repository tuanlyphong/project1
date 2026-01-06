package com.example.myapplication// ← Change to YOUR package name

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import java.util.*

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val waveformData = LinkedList<Float>()
    private val maxDataPoints = 300  // 3 seconds at 100Hz

    // Auto-scaling variables
    private var minValue = Float.MAX_VALUE
    private var maxValue = Float.MIN_VALUE
    private var amplificationFactor = 2.0f  // Amplify the signal

    private val paint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        color = Color.argb(50, 255, 255, 255)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint().apply {
        color = Color.argb(150, 255, 255, 255)
        textSize = 24f
        isAntiAlias = true
    }

    private val path = Path()

    init {
        setBackgroundColor(Color.BLACK)
    }

    fun addDataPoint(value: Float) {
        synchronized(waveformData) {
            waveformData.add(value)
            if (waveformData.size > maxDataPoints) {
                waveformData.removeFirst()
            }

            // Update min/max for auto-scaling (use last 100 points)
            if (waveformData.size > 50) {
                val recent = waveformData.takeLast(100)
                minValue = recent.minOrNull() ?: minValue
                maxValue = recent.maxOrNull() ?: maxValue
            }
        }
        postInvalidate()
    }

    fun clear() {
        synchronized(waveformData) {
            waveformData.clear()
            minValue = Float.MAX_VALUE
            maxValue = Float.MIN_VALUE
        }
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        // Draw grid
        drawGrid(canvas, width, height)

        // Draw waveform
        synchronized(waveformData) {
            if (waveformData.size < 2) return

            path.reset()

            val xStep = width / maxDataPoints
            var x = 0f
            var started = false

            // Calculate range for better scaling
            val range = maxValue - minValue
            val center = (maxValue + minValue) / 2f

            // If range is too small (flat signal), use a minimum range
            val effectiveRange = if (range < 1000f) 5000f else range

            for (value in waveformData) {
                // Normalize around center with amplification
                var normalized = (value - center) / effectiveRange * amplificationFactor

                // Map to screen coordinates (center at 50% height)
                var y = height / 2f - (normalized * height * 0.4f)

                // Clamp to screen bounds
                y = y.coerceIn(0f, height)

                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }

                x += xStep
            }

            canvas.drawPath(path, paint)

            // Draw scale info
            canvas.drawText("Range: ${range.toInt()}", 10f, 30f, textPaint)
            canvas.drawText("Amp: ${amplificationFactor}x", 10f, 60f, textPaint)
        }
    }

    private fun drawGrid(canvas: Canvas, width: Float, height: Float) {
        // Horizontal lines (more lines for better reference)
        for (i in 0..8) {
            val y = height * i / 8
            canvas.drawLine(0f, y, width, y, gridPaint)
        }

        // Vertical lines
        for (i in 0..10) {
            val x = width * i / 10
            canvas.drawLine(x, 0f, x, height, gridPaint)
        }

        // Draw center line (brighter)
        val centerPaint = Paint(gridPaint).apply {
            color = Color.argb(100, 255, 255, 255)
        }
        canvas.drawLine(0f, height / 2f, width, height / 2f, centerPaint)
    }

    // Add method to adjust amplification
    fun setAmplification(factor: Float) {
        amplificationFactor = factor.coerceIn(0.5f, 10f)
        postInvalidate()
    }
}
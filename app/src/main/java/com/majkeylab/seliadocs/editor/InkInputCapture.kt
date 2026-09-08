package com.majkeylab.seliadocs.editor

import android.graphics.Matrix
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import kotlin.math.PI
import kotlin.math.hypot

/** Retains real input until native handoff, so detachment cannot discard a finished stroke. */
internal class InkInputCapture(
    event: MotionEvent,
    pointerId: Int,
    private val brush: Brush,
    inputTransform: Matrix,
    view: View,
) {
    private val startTime = event.eventTime
    private val transform = Matrix(inputTransform)
    private val inputs = MutableStrokeInputBatch()
    private val point = FloatArray(2)
    private val tool = when (event.getToolType(event.findPointerIndex(pointerId))) {
        MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> InputToolType.STYLUS
        MotionEvent.TOOL_TYPE_FINGER -> InputToolType.TOUCH
        else -> InputToolType.UNKNOWN
    }
    private val pressure = supports(event, MotionEvent.AXIS_PRESSURE)
    private val tilt = supports(event, MotionEvent.AXIS_TILT)
    private val orientation = supports(event, MotionEvent.AXIS_ORIENTATION)
    private val unitLength = Matrix().let { physical ->
        check(transform.invert(physical))
        ViewCompat.transformMatrixToGlobal(view, physical)
        val metrics = view.resources.displayMetrics
        physical.postScale(2.54f / metrics.xdpi, 2.54f / metrics.ydpi)
        val values = FloatArray(9)
        physical.getValues(values)
        (hypot(values[0], values[1]) + hypot(values[3], values[4])) / 2f
    }
    private var lastTime = -1L
    private var lastX = Float.NaN
    private var lastY = Float.NaN

    init { add(event, pointerId, includeHistory = false) }

    private fun supports(event: MotionEvent, axis: Int): Boolean =
        tool == InputToolType.STYLUS && event.device?.getMotionRange(axis, event.source) != null

    fun add(event: MotionEvent, pointerId: Int, includeHistory: Boolean = true) {
        val index = event.findPointerIndex(pointerId)
        if (index < 0) return
        fun sample(history: Int?) {
            val time = history?.let(event::getHistoricalEventTime) ?: event.eventTime
            if (time < startTime || time < lastTime) return
            point[0] = history?.let { event.getHistoricalX(index, it) } ?: event.getX(index)
            point[1] = history?.let { event.getHistoricalY(index, it) } ?: event.getY(index)
            transform.mapPoints(point)
            if (time == lastTime && point[0] == lastX && point[1] == lastY) return
            fun axis(axis: Int): Float = history?.let { event.getHistoricalAxisValue(axis, index, it) }
                ?: event.getAxisValue(axis, index)
            val angle = if (orientation) {
                val fullTurn = (2 * PI).toFloat()
                ((axis(MotionEvent.AXIS_ORIENTATION) + (5 * PI / 2).toFloat()) % fullTurn + fullTurn) % fullTurn
            } else StrokeInput.NO_ORIENTATION
            inputs.add(
                tool, point[0], point[1], time - startTime, unitLength,
                if (pressure) axis(MotionEvent.AXIS_PRESSURE).coerceIn(0f, 1f) else StrokeInput.NO_PRESSURE,
                if (tilt) axis(MotionEvent.AXIS_TILT).coerceIn(0f, (PI / 2).toFloat()) else StrokeInput.NO_TILT,
                angle,
            )
            lastTime = time
            lastX = point[0]
            lastY = point[1]
        }
        if (includeHistory) repeat(event.historySize) { sample(it) }
        sample(null)
    }

    fun toStroke(): Stroke = Stroke(brush, inputs)
}

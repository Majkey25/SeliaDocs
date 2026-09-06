package com.majkeylab.seliadocs.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.ink.brush.InputToolType
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkBrushRenderingTest {
    @Test
    fun pressureChangesRenderedPenWidthAfterSaveRoundTrip() {
        val light = renderedWidth(BrushKind.RESPONSIVE_PEN, pressure = 0.15f)
        val medium = renderedWidth(BrushKind.RESPONSIVE_PEN, pressure = 0.5f)
        val firm = renderedWidth(BrushKind.RESPONSIVE_PEN, pressure = 0.9f)
        assertTrue("Light pressure must still draw", light > 0)
        assertTrue("Pressure below 80% must change width: $light -> $medium", medium > light)
        assertTrue(firm > medium)
        assertTrue("Pressure did not widen the pen: $light -> $firm", firm > light * 1.5f)
    }

    @Test
    fun touchPressureDoesNotChangePenWidth() {
        val light = renderedWidth(BrushKind.RESPONSIVE_PEN, pressure = 0.15f, toolType = InputToolType.TOUCH)
        val firm = renderedWidth(BrushKind.RESPONSIVE_PEN, pressure = 0.9f, toolType = InputToolType.TOUCH)
        assertTrue(light > 0)
        assertEquals(light, firm)
    }

    @Test
    fun missingPressureKeepsTheSameVisibleWidthAsTouch() {
        val missing = renderedWidth(BrushKind.RESPONSIVE_PEN, pressure = StrokeInput.NO_PRESSURE)
        val touch = renderedWidth(BrushKind.RESPONSIVE_PEN, toolType = InputToolType.TOUCH)
        assertTrue(missing > 0)
        assertEquals(touch, missing)
    }

    @Test
    fun tiltAndOrientationChangeRenderedPencilTipAfterSaveRoundTrip() {
        val upright = renderedWidth(BrushKind.PENCIL)
        val tilted = renderedWidth(BrushKind.PENCIL, tilt = 1.2f)
        val rotated = renderedWidth(BrushKind.PENCIL, tilt = 1.2f, orientation = Math.PI.toFloat() / 2f)
        assertTrue("Upright pencil must draw", upright > 0)
        assertTrue("Tilt did not widen the pencil: $upright -> $tilted", tilted > upright * 1.5f)
        assertTrue("Orientation did not rotate the pencil tip: $tilted -> $rotated", rotated < tilted / 1.5f)
        assertTrue("Rotated pencil must still draw", rotated > 0)
    }

    private fun renderedWidth(
        kind: BrushKind,
        pressure: Float = 0.8f,
        tilt: Float = 0f,
        orientation: Float = 0f,
        toolType: InputToolType = InputToolType.STYLUS,
    ): Int {
        val inputs = MutableStrokeInputBatch()
        repeat(27) { index ->
            inputs.add(toolType, 64f, 24f + index * 8f, index * 16L, 0.01f, pressure, tilt, orientation)
        }
        val stroke = Stroke(InkCodec.createBrush(kind, 0xFF202124.toInt(), 16f), inputs)
        val restored = InkCodec.decode(InkCodec.encode(stroke))
        val bitmap = Bitmap.createBitmap(128, 256, Bitmap.Config.ARGB_8888)
        return try {
            CanvasStrokeRenderer.create().draw(Canvas(bitmap), restored, Matrix())
            (0 until bitmap.width).count { x -> Color.alpha(bitmap.getPixel(x, 128)) > 24 }
        } finally {
            bitmap.recycle()
        }
    }
}

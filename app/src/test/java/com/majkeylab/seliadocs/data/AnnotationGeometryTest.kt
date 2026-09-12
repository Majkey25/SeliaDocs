package com.majkeylab.seliadocs.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationGeometryTest {
    @Test
    fun legacySourceIdsRemainOpaqueRoomKeys() {
        listOf("page.legacy", "page.legacy · Řeč: 1", "https://example.com/legacy-id").forEach { id ->
            validateAnnotationFields(ElementKind.TEXT, null, null, id, "0,0,1,1")
        }
        listOf("", "  ", "x".repeat(1_025)).forEach { id ->
            assertTrue(runCatching { validateAnnotationFields(ElementKind.TEXT, null, null, id, "0,0,1,1") }.isFailure)
        }
    }

    @Test
    fun rectanglesRoundTripInReadingOrderWithoutLosingPrecision() {
        val rects = listOf(AnnotationRect(0f, 0f, 0.75f, 0.4f), AnnotationRect(0.1f, 0.6f, 1f, 1f))
        assertEquals(rects, decodeAnnotationRects(encodeAnnotationRects(rects)))
        assertEquals(emptyList<AnnotationRect>(), decodeAnnotationRects(null))
        assertEquals(rects.first(), decodeSourceRect(encodeSourceRect(rects.first())))
    }

    @Test
    fun malformedAndUnboundedGeometryIsRejectedRatherThanSilentlyDropped() {
        listOf("", "0,0,1", "0,0,1,1,2", "NaN,0,1,1", "0,0,Infinity,1", "-0.1,0,1,1",
            "0,0,1.01,1", "0.8,0,0.2,1", "0,0,0,1", "0,0,1,1\n", "x".repeat(100_001),
            List(2_001) { "0,0,1,1" }.joinToString("\n")).forEach { malformed ->
            assertTrue(malformed.take(40), runCatching { decodeAnnotationRects(malformed) }.isFailure)
        }
        assertTrue(runCatching { encodeAnnotationRects(emptyList()) }.isFailure)
        assertTrue(runCatching { decodeSourceRect("0,0,1,1\n0,0,1,1") }.isFailure)
    }
}

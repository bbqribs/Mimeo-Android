package com.mimeo.android.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollIndicatorTest {

    @Test
    fun thumbIsAbsentWithoutScrollableOverflow() {
        assertNull(
            verticalScrollThumbGeometry(
                viewportHeightPx = 600f,
                maxScrollValue = 0,
                scrollValue = 0,
                minThumbHeightPx = 40f,
            ),
        )
    }

    @Test
    fun draggableThumbPaintsInsideItsLocalTrailingEdge() {
        assertEquals(
            991f,
            verticalScrollThumbLeftPx(
                viewportWidthPx = 1_000f,
                thumbWidthPx = 3f,
                endPaddingPx = 6f,
            ),
            0.001f,
        )
        assertEquals(0f, verticalScrollThumbLeftPx(4f, 3f, 6f), 0.001f)
    }

    @Test
    fun thumbGeometryTracksScrollEndpointsAndMidpoint() {
        val atStart = geometry(scrollValue = 0)
        val atMiddle = geometry(scrollValue = 500)
        val atEnd = geometry(scrollValue = 1_000)

        assertEquals(0f, atStart.topPx, 0.001f)
        assertEquals(atStart.travelPx / 2f, atMiddle.topPx, 0.001f)
        assertEquals(atStart.travelPx, atEnd.topPx, 0.001f)
        assertEquals(atStart.heightPx, atEnd.heightPx, 0.001f)
    }

    @Test
    fun thumbGeometryEnforcesMinimumThumbHeight() {
        val geometry = checkNotNull(
            verticalScrollThumbGeometry(
                viewportHeightPx = 600f,
                maxScrollValue = 100_000,
                scrollValue = 50_000,
                minThumbHeightPx = 40f,
            ),
        )

        assertEquals(40f, geometry.heightPx, 0.001f)
        assertEquals(560f, geometry.travelPx, 0.001f)
        assertEquals(280f, geometry.topPx, 0.001f)
    }

    @Test
    fun dragMappingIsProportionalMonotonicAndClamped() {
        val pointerPositions = listOf(-100f, 0f, 100f, 187.5f, 300f, 375f, 900f)
        val values = pointerPositions.map { pointerY ->
            scrollValueForThumbDrag(
                pointerYPx = pointerY,
                grabFraction = 0f,
                viewportHeightPx = 600f,
                maxScrollValue = 1_000,
                minThumbHeightPx = 40f,
            )
        }

        assertEquals(0, values.first())
        assertEquals(1_000, values.last())
        assertTrue(values.zipWithNext().all { (left, right) -> left <= right })
        assertTrue(values[3] in 495..505)
    }

    @Test
    fun dragMappingRecomputesForChangedViewportAndContent() {
        val shortViewport = checkNotNull(
            verticalScrollThumbGeometry(
                viewportHeightPx = 400f,
                maxScrollValue = 1_600,
                scrollValue = 800,
                minThumbHeightPx = 40f,
            ),
        )
        val tallViewport = checkNotNull(
            verticalScrollThumbGeometry(
                viewportHeightPx = 800f,
                maxScrollValue = 800,
                scrollValue = 400,
                minThumbHeightPx = 40f,
            ),
        )

        assertEquals(80f, shortViewport.heightPx, 0.001f)
        assertEquals(400f, tallViewport.heightPx, 0.001f)
        assertEquals(shortViewport.travelPx / 2f, shortViewport.topPx, 0.001f)
        assertEquals(tallViewport.travelPx / 2f, tallViewport.topPx, 0.001f)
        assertEquals(
            800,
            scrollValueForThumbDrag(
                pointerYPx = 800f,
                grabFraction = 1f,
                viewportHeightPx = 800f,
                maxScrollValue = 800,
                minThumbHeightPx = 40f,
            ),
        )
    }

    @Test
    fun dragMappingIsSafeWhenThumbHasNoTravel() {
        val geometry = checkNotNull(
            verticalScrollThumbGeometry(
                viewportHeightPx = 40f,
                maxScrollValue = 1_000,
                scrollValue = 500,
                minThumbHeightPx = 40f,
            ),
        )

        assertEquals(0f, geometry.travelPx, 0.001f)
        assertEquals(
            0,
            scrollValueForThumbDrag(
                pointerYPx = 20f,
                grabFraction = 0.5f,
                viewportHeightPx = 40f,
                maxScrollValue = 1_000,
                minThumbHeightPx = 40f,
            ),
        )
    }

    private fun geometry(scrollValue: Int): VerticalScrollThumbGeometry = checkNotNull(
        verticalScrollThumbGeometry(
            viewportHeightPx = 600f,
            maxScrollValue = 1_000,
            scrollValue = scrollValue,
            minThumbHeightPx = 40f,
        ),
    )
}

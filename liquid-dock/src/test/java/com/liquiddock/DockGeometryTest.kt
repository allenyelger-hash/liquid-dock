package com.liquiddock

import androidx.compose.ui.unit.dp
import com.liquiddock.internal.dockLensScaleX
import com.liquiddock.internal.dockLensScaleY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockGeometryTest {
    @Test
    fun allFourTargetsAndOpticalEdgesFitPhoneWidths() {
        for (width in intArrayOf(320, 360, 393, 411, 430, 448, 480, 600, 840, 1024)) {
            val g = dockGeometry(width.dp)
            val margin = (width - g.bodyWidth.value - g.actionGap.value - g.fabSize.value) / 2f
            val endExpansionShift = g.slotWidth(4).value * 1.5f * g.baseExpansionX
            assertTrue("four accessible targets at $width", g.slotWidth(4).value >= 48f)
            assertTrue("complete end lens with panel shift at $width", margin >= g.horizontalOverhang(4).value + g.maxPanelShift.value)
            assertTrue(
                "complete end lens with base expansion and panel shift at $width",
                margin + 0.001f >= g.horizontalOverhang(4).value + endExpansionShift + g.maxPanelShift.value,
            )
            assertTrue("resting capsule stays inside at $width", g.listInsetH.value >= g.slotWidth(4).value * (g.restWidthRatio - 1f) / 2)
            assertTrue(g.pressedPillHeight > g.height)
            assertTrue(g.maxPillWidth(4) > g.slotWidth(4))
            assertTrue(g.opticalInset.value >= (g.maxPillHeight.value - g.height.value) / 2f + 8f)
        }
    }

    @Test
    fun pageAvoidanceContainsAnimatedLensAndShadow() {
        for (width in intArrayOf(320, 411, 600, 1024)) {
            val g = dockGeometry(width.dp)
            assertTrue(dockOccupiedHeight(width.dp) > g.maxPillHeight)
            assertTrue(dockClear(width.dp) > dockOccupiedHeight(width.dp))
            assertEquals(g.height.value + g.opticalInset.value * 2 + 12f, dockOccupiedHeight(width.dp).value, 0.001f)
        }
    }

    @Test
    fun fastTravelLengthensAndFlattensWithinCapturedEnvelope() {
        for (width in intArrayOf(320, 360, 393, 411, 430, 480, 600)) {
            val g = dockGeometry(width.dp)
            val rest = g.pressedPillHeight / g.pillHeight
            assertTrue(dockLensScaleX(rest, 30f, g) > dockLensScaleX(rest, 0f, g))
            assertTrue(dockLensScaleY(rest, 30f, g) < dockLensScaleY(rest, 0f, g))
            assertEquals(dockLensScaleX(rest, 30f, g), dockLensScaleX(rest, -30f, g), 0.0001f)
            assertTrue(g.slotWidth(4) * g.restWidthRatio * dockLensScaleX(rest, 30f, g) <= g.maxPillWidth(4))
            assertEquals(1f, dockLensScaleY(1f, 0f, g), 0.0001f)
            assertTrue("spring release allows subtle jelly squash", dockLensScaleY(0.97f, 0f, g) in 0.95f..0.99f)
        }
    }

    @Test
    fun baselineAspectRatiosAndEndSlotFabTangencyStayLockedAcrossWidths() {
        for (width in intArrayOf(320, 360, 393, 411, 430, 448, 480, 600, 840, 1024)) {
            val g = dockGeometry(width.dp)
            val pillWidth = g.slotWidth(4).value * g.restWidthRatio
            val steadyLensWidth = g.slotWidth(4).value * g.pressedWidthRatio
            assertEquals("base bar aspect at $width", 4.5800f, g.bodyWidth.value / g.height.value, 0.01f)
            assertEquals("fab circle ratio at $width", 1.0000f, g.fabSize.value / g.height.value, 0.005f)
            assertEquals("action gap ratio at $width", 0.1860f, g.actionGap.value / g.height.value, 0.005f)
            assertEquals("resting dark pill aspect at $width", 1.6000f, pillWidth / g.pillHeight.value, 0.01f)
            assertEquals("steady pressed bubble aspect at $width", 1.4150f, steadyLensWidth / g.pressedPillHeight.value, 0.01f)

            val unscaledSlot3FromRight = g.listInsetH.value + g.slotWidth(4).value * 0.5f
            val endExpShift = g.slotWidth(4).value * 1.5f * g.baseExpansionX
            val rightProtrusion = (steadyLensWidth / 2f - unscaledSlot3FromRight) + endExpShift
            val gapToFab = g.actionGap.value - rightProtrusion
            assertTrue("Slot-3 steady bubble kisses Fab without overlap at $width ($gapToFab dp)", gapToFab in 0.02f..0.5f)
            assertTrue("Dock clearance stays bounded at $width", dockClear(width.dp) <= 176.dp)
        }
        assertEquals(56.2f, dockGeometry(360.dp).height.value, 0.1f)
        assertEquals(61.3f, dockGeometry(393.dp).height.value, 0.1f)
        assertEquals(64.2f, dockGeometry(411.dp).height.value, 0.1f)
        val g430 = dockGeometry(430.dp)
        assertEquals(65.9f, g430.height.value, 0.1f)
        assertEquals(57.2f, g430.pillHeight.value, 0.15f)
        assertEquals(78.6f, g430.pressedPillHeight.value, 0.15f)
    }

    @Test
    fun supportsTwoToFiveTabsSafelyInsideBaseCapsule() {
        val widths = intArrayOf(320, 360, 393, 411, 430, 448, 480, 600, 840, 1024)
        for (width in widths) {
            val g = dockGeometry(width.dp)
            val restScale = g.pressedPillHeight / g.pillHeight
            for (tabCount in 2..5) {
                val restRatio = g.restWidthRatio(tabCount)
                val pressedRatio = g.pressedWidthRatio(tabCount)
                val slotW = g.slotWidth(tabCount).value
                val steadyLensWidth = slotW * pressedRatio
                val unscaledLastSlotFromRight = g.listInsetH.value + slotW * 0.5f
                val endExpShift = slotW * (((tabCount - 1) / 2f) * g.baseExpansionX)
                val rightProtrusion = (steadyLensWidth / 2f - unscaledLastSlotFromRight) + endExpShift
                val gapToFab = g.actionGap.value - rightProtrusion
                assertTrue(
                    "resting pill stays inside base for $tabCount tabs at $width",
                    g.listInsetH.value >= slotW * (restRatio - 1f) / 2f,
                )
                assertTrue(
                    "pressed bubble widens beyond resting pill for $tabCount tabs at $width",
                    pressedRatio > restRatio,
                )
                assertTrue(
                    "last-slot steady bubble stays clear of FAB for $tabCount tabs at $width ($gapToFab dp)",
                    gapToFab > 0.02f,
                )
                assertTrue(
                    "lens scale stays within capture envelope for $tabCount tabs at $width",
                    slotW * restRatio * dockLensScaleX(restScale, 30f, g, tabCount) <= g.maxPillWidth(tabCount).value,
                )
            }
        }
    }

    @Test
    fun wideScreensKeepCompactCenteredDock() {
        val g = dockGeometry(1024.dp)
        assertTrue(g.bodyWidth.value <= 380f)
        assertTrue(g.bodyWidth + g.actionGap + g.fabSize < g.stripWidth)
    }
}

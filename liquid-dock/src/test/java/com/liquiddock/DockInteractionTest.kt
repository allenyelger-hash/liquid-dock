@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.liquiddock

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.liquiddock.internal.DampedDragAnimation
import com.liquiddock.internal.createDockDragAnimation
import com.liquiddock.internal.dockLensLift
import com.liquiddock.internal.dockLensOpacity
import com.liquiddock.internal.dockLensScaleY
import com.liquiddock.internal.dockTargetIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockInteractionTest {
    @Test
    fun dragTargetsRoundAndClampAtBothEnds() {
        assertEquals(0, dockTargetIndex(-2f, 4))
        assertEquals(0, dockTargetIndex(0.49f, 4))
        assertEquals(1, dockTargetIndex(0.51f, 4))
        assertEquals(2, dockTargetIndex(1.8f, 4))
        assertEquals(3, dockTargetIndex(20f, 4))
    }

    @Test
    fun rapidRetargetThenNewPressCannotBeShrunkByOldRelease() = runTest {
        val clock = frameClock { testScheduler.currentTime }
        val owner = Job(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + owner + clock)
        val animation = animation(scope)
        animation.animateToValue(3f)
        advanceTimeBy(48)
        animation.animateToValue(1f)
        advanceTimeBy(48)
        animation.press()
        advanceTimeBy(1400)
        runCurrent()
        assertEquals(1f, animation.targetValue, 0.001f)
        assertEquals(1f, animation.value, 0.01f)
        assertTrue(animation.pressProgress > 0.99f)
        assertTrue(animation.clarity > 0.99f)
        animation.release()
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(0f, animation.pressProgress, 0.001f)
        assertEquals(0f, animation.clarity, 0.001f)
        assertEquals(1f, animation.scaleX, 0.001f)
        assertEquals(1f, animation.scaleY, 0.001f)
        scope.cancel()
    }

    @Test
    fun disposalCancelsAnimationsWaitingForFrames() = runTest {
        val clock = frameClock { testScheduler.currentTime }
        val owner = Job(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + owner + clock)
        val animation = animation(scope)
        animation.animateToValue(3f)
        advanceTimeBy(32)
        scope.cancel()
        runCurrent()
        assertTrue(owner.isCancelled)
        assertFalse(owner.children.any())
        val stoppedValue = animation.value
        advanceTimeBy(2000)
        assertEquals(stoppedValue, animation.value, 0f)
    }

    @Test
    fun cancelledTravelReturnsAndRepeatedTargetsLeaveNoReleaseJobs() = runTest {
        val clock = frameClock { testScheduler.currentTime }
        val owner = Job(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + owner + clock)
        val animation = animation(scope)
        animation.press()
        animation.updateValue(3f)
        advanceTimeBy(96)
        // ACTION_CANCEL asks for the current business selection, never commits the dragged target.
        animation.updateValue(0f)
        animation.release()
        advanceTimeBy(2400)
        runCurrent()
        assertEquals(0f, animation.value, 0.001f)
        assertEquals(0f, animation.pressProgress, 0.001f)
        repeat(5) {
            animation.animateToValue(2f)
            advanceTimeBy(32)
        }
        advanceTimeBy(2400)
        runCurrent()
        assertEquals(2f, animation.value, 0.001f)
        assertEquals(0f, animation.pressProgress, 0.001f)
        assertEquals(1f, animation.scaleX, 0.001f)
        assertEquals(1f, animation.scaleY, 0.001f)
        assertFalse(owner.children.any())
        scope.cancel()
    }

    private fun animation(scope: CoroutineScope, pressedScale: Float = 1.1828f / 0.8724f) = DampedDragAnimation(
        animationScope = scope,
        initialValue = 0f,
        valueRange = 0f..3f,
        visibilityThreshold = 0.001f,
        initialScale = 1f,
        pressedScale = pressedScale,
        onDragStarted = {},
        onDragStopped = {},
        onDragCancelled = {},
        onDrag = { _, _ -> },
    )

    @Test
    fun springReleaseProducesDampedJellySquashThenSettles() = runTest {
        val owner = Job(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + owner + frameClock { testScheduler.currentTime })
        val g = dockGeometry(430.dp)
        val animation = animation(scope, pressedScale = g.pressedPillHeight / g.pillHeight)
        animation.press()
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(1f, animation.pressProgress, 0.01f)
        animation.release()
        var minScaleY = animation.scaleY
        repeat(100) {
            advanceTimeBy(16)
            runCurrent()
            if (animation.scaleY < minScaleY) minScaleY = animation.scaleY
        }
        assertTrue("Underdamped jelly release dips briefly below rest height ($minScaleY)", minScaleY in 0.93f..0.995f)
        assertEquals(0f, animation.pressProgress, 0.001f)
        assertEquals(0f, animation.clarity, 0.001f)
        assertEquals(1f, animation.scaleX, 0.001f)
        assertEquals(1f, animation.scaleY, 0.001f)
        scope.cancel()
        runCurrent()
        assertFalse(owner.children.any())
    }

    @Test
    fun tapTravelStretchesWithoutMoveEventsAndSettles() = runTest {
        val owner = Job(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + owner + frameClock { testScheduler.currentTime })
        val animation = animation(scope)
        animation.animateToValue(3f)
        advanceTimeBy(80)
        runCurrent()
        assertTrue("Tap travel must supply shape velocity", animation.velocity > 1f)
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(3f, animation.value, 0.001f)
        assertEquals(0f, animation.velocity, 0.001f)
        assertEquals(0f, animation.clarity, 0.001f)
        assertFalse(owner.children.any())
        scope.cancel()
    }

    @Test
    fun materialReturnsBeforeRaisedGeometryFinishesSinking() = runTest {
        val owner = Job(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + owner + frameClock { testScheduler.currentTime })
        val animation = animation(scope)
        animation.press()
        advanceTimeBy(1000)
        assertEquals(1f, animation.clarity, 0.001f)
        animation.release()
        advanceTimeBy(144)
        runCurrent()
        assertTrue("Dark material has returned", animation.clarity < 0.01f)
        assertTrue("Lens still projects above the resting pill", animation.scaleY > 1.01f)
        assertTrue(animation.pressProgress > 0f)
        // A new press must cancel both material and geometry stages of the old release.
        animation.press()
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(1f, animation.clarity, 0.001f)
        assertTrue(animation.scaleY > 1.3f)
        scope.cancel()
        runCurrent()
        assertFalse(owner.children.any())
    }

    @Test
    fun releaseKeepsActiveLensRefractionForEveryOverhangingFrame() = runTest {
        val owner = Job(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + owner + frameClock { testScheduler.currentTime })
        val g = dockGeometry(430.dp)
        val pressedScale = g.pressedPillHeight / g.pillHeight
        val animation = animation(scope, pressedScale = pressedScale)
        animation.press()
        var maxPressScaleY = 1f
        repeat(60) {
            advanceTimeBy(16)
            runCurrent()
            if (animation.scaleY > maxPressScaleY) maxPressScaleY = animation.scaleY
        }
        assertTrue("Critically damped press must not vertically overshoot ($maxPressScaleY vs $pressedScale)", maxPressScaleY <= pressedScale + 0.005f)
        assertEquals(1f, animation.pressProgress, 0.01f)
        animation.release()
        var overhangingFrames = 0
        repeat(80) {
            advanceTimeBy(16)
            runCurrent()
            val scaleY = dockLensScaleY(animation.scaleY, animation.velocity, g)
            val lensHeightDp = g.pillHeight.value * scaleY
            val lift = dockLensLift(scaleY, animation.pressProgress, g)
            if (lensHeightDp > g.height.value) {
                overhangingFrames++
                assertTrue(
                    "Overhanging lens ($lensHeightDp dp > ${g.height.value} dp) must retain active refraction ($lift)",
                    lift > 0.35f,
                )
                assertEquals(1f, dockLensOpacity(lift), 0.001f)
            }
        }
        assertTrue("Release trajectory must sample multiple overhanging frames ($overhangingFrames)", overhangingFrames >= 3)
        val settledLift = dockLensLift(dockLensScaleY(animation.scaleY, animation.velocity, g), animation.pressProgress, g)
        assertEquals(0f, settledLift, 0.005f)
        assertEquals(0f, dockLensOpacity(settledLift), 0.05f)
        scope.cancel()
        runCurrent()
        assertFalse(owner.children.any())
    }

    @Test
    fun streamingHighFrequencyMoveUpdatesAdvanceEverySingleFrameWithoutStarvation() = runTest {
        val owner = Job(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + owner + frameClock { testScheduler.currentTime })
        val animation = animation(scope)
        val barSize = IntSize(400, 62)
        var uptimeMs = 0L
        var xPx = 50f
        animation.handleDragStart(Offset(xPx, 31f), uptimeMs)
        runCurrent()
        var prevValue = animation.value

        // 240Hz touch MOVE stream: 4 updates every 16ms frame across 10 consecutive VSYNC frames.
        repeat(10) { frame ->
            repeat(4) {
                uptimeMs += 4L
                xPx += 7.5f
                animation.handleDrag(barSize, Offset(7.5f, 0f), Offset(xPx, 31f), uptimeMs)
                animation.updateValue(((xPx - 50f) / 100f).coerceIn(0f, 3f), dragging = true)
                runCurrent()
                advanceTimeBy(4)
                runCurrent()
            }
            assertTrue(
                "Frame $frame must advance animation.value (${animation.value} > $prevValue)",
                animation.value > prevValue + 0.005f,
            )
            assertTrue("Frame $frame must maintain active velocity (${animation.velocity})", animation.velocity > 0.5f)
            prevValue = animation.value
        }

        // Held finger pauses at 3.0f: value converges and input velocity decays back to 0 after 90ms idle clock.
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(3f, animation.value, 0.01f)
        assertEquals(0f, animation.velocity, 0.02f)
        prevValue = animation.value

        // 120Hz reverse MOVE stream: 2 updates every 16ms frame across 8 consecutive VSYNC frames.
        repeat(8) { frame ->
            repeat(2) {
                uptimeMs += 8L
                xPx -= 15f
                animation.handleDrag(barSize, Offset(-15f, 0f), Offset(xPx, 31f), uptimeMs)
                animation.updateValue(((xPx - 50f) / 100f).coerceIn(0f, 3f), dragging = true)
                runCurrent()
                advanceTimeBy(8)
                runCurrent()
            }
            assertTrue(
                "Reverse frame $frame must advance animation.value (${animation.value} < $prevValue)",
                animation.value < prevValue - 0.005f,
            )
            prevValue = animation.value
        }

        // Held finger stops moving again: velocity recovers to 0 while held, then release settles cleanly.
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(0f, animation.velocity, 0.02f)

        animation.handleDragEnd()
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(0f, animation.pressProgress, 0.001f)
        assertFalse(owner.children.any())
        scope.cancel()
    }

    @Test
    fun offCenterPressOnCurrentTabStaysCenteredThenTracksContinuousSlotOnDrag() = runTest {
        val owner = Job(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + owner + frameClock { testScheduler.currentTime })
        var selected = 2
        val requests = mutableListOf<Int>()
        val controller = createDockDragAnimation(
            scope = scope,
            tabsCount = 4,
            pressedScale = 1.35f,
            selectedTabIndex = { selected },
            onTabSelected = { requests += it; selected = it },
            tabWidthPx = { 100f },
            insetPx = { 20f },
            bodyWidthPx = { 440f },
        )
        val animation = controller.animation

        val startX = 20f + (2.32f + 0.5f) * 100f
        animation.handleDragStart(Offset(startX, 31f), 0L)
        assertEquals(2f, animation.targetValue, 0.0001f)
        advanceTimeBy(160)
        runCurrent()
        assertEquals(2f, animation.value, 0.001f)
        assertTrue(requests.isEmpty())

        animation.handleDrag(IntSize(440, 62), Offset(-107f, 0f), Offset(startX - 107f, 31f), 176L)
        assertEquals(1.25f, animation.targetValue, 0.001f)
        advanceTimeBy(32)
        runCurrent()
        assertTrue(animation.value in 1.25f..1.99f)

        animation.handleDragCancel()
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(2f, animation.value, 0.001f)
        assertTrue(requests.isEmpty())
        assertFalse(owner.children.any())
        scope.cancel()
    }

    @Test
    fun heldDwellSwitchesLiveWhileRapidSweepSkipsIntermediateSlots() = runTest {
        val owner = Job(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + owner + frameClock { testScheduler.currentTime })
        var selected = 0
        val requests = mutableListOf<Int>()
        var onObserved: (Int) -> Unit = {}
        val controller = createDockDragAnimation(
            scope = scope,
            tabsCount = 4,
            pressedScale = 1.35f,
            selectedTabIndex = { selected },
            onTabSelected = { requests += it; selected = it; onObserved(it) },
            tabWidthPx = { 100f },
            insetPx = { 20f },
            bodyWidthPx = { 440f },
        )
        val animation = controller.animation
        onObserved = controller.onSelectionObserved

        var x = 70f
        var t = 0L
        animation.handleDragStart(Offset(x, 31f), t)

        x += 100f; t += 16L
        animation.handleDrag(IntSize(440, 62), Offset(100f, 0f), Offset(x, 31f), t)
        advanceTimeBy(48); runCurrent()

        x += 100f; t += 48L
        animation.handleDrag(IntSize(440, 62), Offset(100f, 0f), Offset(x, 31f), t)
        advanceTimeBy(48); runCurrent()

        x += 100f; t += 48L
        animation.handleDrag(IntSize(440, 62), Offset(100f, 0f), Offset(x, 31f), t)
        assertTrue("Rapid sweep (<95ms) must not dispatch intermediate tabs", requests.isEmpty())

        advanceTimeBy(144); runCurrent()
        assertEquals(listOf(3), requests)
        assertTrue(animation.isPressed)

        x -= 200f; t += 160L
        animation.handleDrag(IntSize(440, 62), Offset(-200f, 0f), Offset(x, 31f), t)
        advanceTimeBy(32); runCurrent()
        animation.handleDragEnd()
        advanceTimeBy(2000); runCurrent()
        assertEquals(listOf(3, 1), requests)
        assertEquals(1f, animation.value, 0.001f)
        assertFalse(owner.children.any())
        scope.cancel()
    }

    @Test
    fun externalNavigationOverridesHeldDwellAndCancelDropsPendingDwell() = runTest {
        val owner = Job(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + owner + frameClock { testScheduler.currentTime })
        var selected = 2
        val requests = mutableListOf<Int>()
        var onObserved: (Int) -> Unit = {}
        val controller = createDockDragAnimation(
            scope = scope,
            tabsCount = 4,
            pressedScale = 1.35f,
            selectedTabIndex = { selected },
            onTabSelected = { requests += it; selected = it; onObserved(it) },
            tabWidthPx = { 100f },
            insetPx = { 20f },
            bodyWidthPx = { 440f },
        )
        val animation = controller.animation
        onObserved = controller.onSelectionObserved

        animation.handleDragStart(Offset(270f, 31f), 0L)
        animation.handleDrag(IntSize(440, 62), Offset(-100f, 0f), Offset(170f, 31f), 16L)
        advanceTimeBy(144); runCurrent()
        assertEquals(listOf(1), requests)

        selected = 0
        controller.onSelectionObserved(0)

        animation.handleDrag(IntSize(440, 62), Offset(200f, 0f), Offset(370f, 31f), 176L)
        advanceTimeBy(160); runCurrent()
        animation.handleDragEnd()
        advanceTimeBy(2000); runCurrent()
        assertEquals(listOf(1), requests)
        assertEquals(0, selected)
        assertEquals(0f, animation.value, 0.001f)

        animation.handleDragStart(Offset(70f, 31f), 2200L)
        animation.handleDrag(IntSize(440, 62), Offset(200f, 0f), Offset(270f, 31f), 2216L)
        advanceTimeBy(48); runCurrent()
        animation.handleDragCancel()
        advanceTimeBy(2000); runCurrent()
        assertEquals(listOf(1), requests)
        assertEquals(0f, animation.value, 0.001f)

        animation.handleDragStart(Offset(370f, 31f), 4300L)
        advanceTimeBy(160); runCurrent()
        assertEquals("Stationary press without horizontal drag must not arm live dwell", listOf(1), requests)
        animation.handleDrag(IntSize(440, 62), Offset(-100f, 0f), Offset(270f, 31f), 4460L)
        advanceTimeBy(80); runCurrent()
        animation.handleDragCancel()
        advanceTimeBy(2000); runCurrent()
        assertEquals(listOf(1), requests)
        assertEquals(0, selected)
        assertEquals(0f, animation.value, 0.001f)
        assertFalse(owner.children.any())
        scope.cancel()
    }

    @Test
    fun rtlMapsRightEdgeToSlotZeroAndLeftEdgeToLastSlot() = runTest {
        val owner = Job(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + owner + frameClock { testScheduler.currentTime })
        var selected = 0
        val requests = mutableListOf<Int>()
        val controller = createDockDragAnimation(
            scope = scope,
            tabsCount = 4,
            pressedScale = 1.35f,
            selectedTabIndex = { selected },
            onTabSelected = { requests += it; selected = it },
            tabWidthPx = { 100f },
            insetPx = { 20f },
            bodyWidthPx = { 440f },
            isLtr = { false },
        )
        val animation = controller.animation

        // In RTL, slot 0 sits on the right (x = 370f) and slot 3 sits on the left (x = 70f).
        animation.handleDragStart(Offset(370f, 31f), 0L)
        assertEquals(0f, animation.targetValue, 0.001f)

        animation.handleDrag(IntSize(440, 62), Offset(-300f, 0f), Offset(70f, 31f), 16L)
        assertEquals(3f, animation.targetValue, 0.001f)
        animation.handleDragEnd()
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(listOf(3), requests)
        assertEquals(3f, animation.value, 0.001f)
        scope.cancel()
    }

    @Test
    fun controllerCancelStopsInFlightTravelAndPendingDwellWhenTabCountChanges() = runTest {
        val owner = Job(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + owner + frameClock { testScheduler.currentTime })
        var selected = 0
        val requests = mutableListOf<Int>()
        val controller = createDockDragAnimation(
            scope = scope,
            tabsCount = 4,
            pressedScale = 1.35f,
            selectedTabIndex = { selected },
            onTabSelected = { requests += it; selected = it },
            tabWidthPx = { 100f },
            insetPx = { 20f },
            bodyWidthPx = { 440f },
        )
        val animation = controller.animation

        animation.handleDragStart(Offset(70f, 31f), 0L)
        animation.handleDrag(IntSize(440, 62), Offset(300f, 0f), Offset(370f, 31f), 16L)
        assertTrue(animation.isPressed)

        // Simulate remember(tabsCount) disposal replacing the controller mid-drag before 95ms dwell fires.
        controller.cancel()
        assertFalse(animation.isPressed)
        val frozenValue = animation.value
        advanceTimeBy(500)
        runCurrent()
        assertTrue("Cancelled dwell must not dispatch stale tab selection", requests.isEmpty())
        assertEquals(frozenValue, animation.value, 0f)
        assertFalse(owner.children.any())
        scope.cancel()
    }

    private fun frameClock(now: () -> Long) = object : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(16)
            return onFrame(now() * 1_000_000)
        }
    }
}

/*
 * Copyright 2025 Kyant. Licensed under the Apache License, Version 2.0.
 * Adapted for liquid-dock; see THIRD_PARTY_NOTICES/AndroidLiquidGlass.md.
 */
package com.liquiddock.internal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val DefaultFrameStepNanos = 16_000_000L
private const val MaxFrameStepNanos = 64_000_000L
private const val VelocityIdleTimeoutNanos = 90_000_000L
private const val InputVelocityDerivativeThreshold = 0.5f

internal class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDragCancelled: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {

    private val tapValueSpec =
        FloatSpringSpec(dampingRatio = 0.82f, stiffness = 600f, visibilityThreshold = visibilityThreshold)
    private val dragValueSpec =
        FloatSpringSpec(dampingRatio = 0.92f, stiffness = 1600f, visibilityThreshold = visibilityThreshold)
    private val inputVelocityTrackSpec =
        FloatSpringSpec(dampingRatio = 1f, stiffness = 1800f, visibilityThreshold = 0.01f)
    private val inputVelocityDecaySpec =
        FloatSpringSpec(dampingRatio = 0.65f, stiffness = 320f, visibilityThreshold = visibilityThreshold * 10f)
    private val pressProgressAnimationSpec =
        spring(0.94f, 430f, 0.001f)
    private val scaleXAnimationSpec =
        spring(0.92f, 420f, 0.001f)
    private val scaleYAnimationSpec =
        spring(0.94f, 420f, 0.001f)
    private val releasePressAnimationSpec =
        spring(0.90f, 460f, 0.001f)
    private val releaseScaleXAnimationSpec =
        spring(0.78f, 470f, 0.001f)
    private val releaseScaleYAnimationSpec =
        spring(0.76f, 470f, 0.001f)

    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val clarityAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation =
        Animatable(initialScale, 0.001f)
    private val scaleYAnimation =
        Animatable(initialScale, 0.001f)

    // Keep superseded work cancelled so a previous release cannot shrink a new press, while
    // value/velocity loops integrate across frames without cancelling at frame-0 on 60-240Hz MOVE streams.
    // The composition-owned scope cancels all of these on disposal.
    private var valueJob: Job? = null
    private var velocityJob: Job? = null
    private var pressJob: Job? = null
    private var releaseJob: Job? = null

    private var currentValue by mutableFloatStateOf(initialValue)
    private var currentValueVelocity by mutableFloatStateOf(0f)
    private var requestedValue by mutableFloatStateOf(initialValue)
    private var useTightDragSpring = false

    private var currentInputVelocity by mutableFloatStateOf(0f)
    private var currentInputVelocityDerivative = 0f
    private var requestedInputVelocity = 0f
    private var inputVelocityDecaying = false
    private var inputMoveSeq = 0L

    var isPressed by mutableStateOf(false)
        private set
    private var isDragging = false

    private val velocityTracker = VelocityTracker()

    val value: Float get() = currentValue
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
    val targetValue: Float get() = requestedValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val clarity: Float get() = clarityAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value

    // A tap also has travel velocity even though it has no MOVE events. Use the
    // dominant signed velocity, without adding the same drag movement twice.
    val velocity: Float get() = if (abs(currentValueVelocity) > abs(currentInputVelocity))
        currentValueVelocity.coerceIn(-40f, 40f) else currentInputVelocity

    val modifier: Modifier = Modifier.pointerInput(this) {
        inspectDragGestures(
            consume = true,
            onDragStart = { down ->
                handleDragStart(down.position, down.uptimeMillis)
            },
            onDragEnd = {
                handleDragEnd()
            },
            onDragCancel = {
                handleDragCancel()
            },
        ) { change, dragAmount ->
            handleDrag(size, dragAmount, change.position, change.uptimeMillis)
        }
    }

    internal fun handleDragStart(position: Offset, uptimeMillis: Long = 0L) {
        isPressed = true
        isDragging = false
        velocityTracker.resetTracking()
        velocityTracker.addPosition(uptimeMillis, position)
        onDragStarted(position)
        press()
    }

    internal fun handleDrag(
        size: IntSize,
        dragAmount: Offset,
        position: Offset,
        uptimeMillis: Long,
    ) {
        if (dragAmount.x != 0f) {
            isDragging = true
        }
        onDrag(size, dragAmount)
        if (dragAmount != Offset.Zero) {
            velocityTracker.addPosition(uptimeMillis, position)
            val slotsPerSecond = velocityTracker.calculateVelocity().x / size.width.coerceAtLeast(1) *
                (valueRange.endInclusive - valueRange.start)
            updateInputVelocity(slotsPerSecond)
        }
    }

    internal fun handleDragEnd() {
        isPressed = false
        isDragging = false
        onDragStopped()
        release()
    }

    internal fun handleDragCancel() {
        isPressed = false
        isDragging = false
        onDragCancelled()
        release()
    }

    internal fun cancel() {
        isPressed = false
        isDragging = false
        valueJob?.cancel()
        valueJob = null
        velocityJob?.cancel()
        velocityJob = null
        pressJob?.cancel()
        pressJob = null
        releaseJob?.cancel()
        releaseJob = null
    }

    fun press() {
        releaseJob?.cancel()
        pressJob?.cancel()
        pressJob = animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { clarityAnimation.animateTo(1f, tween(120, easing = FastOutSlowInEasing)) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        useTightDragSpring = false
        decayInputVelocity()
        releaseJob?.cancel()
        releaseJob = animationScope.launch {
            withFrameNanos { }
            // Join the actual travel job, including cancellation return. Snapshot notifications
            // are not a completion signal: a settled value could leave the old release waiting.
            valueJob?.join()
            pressJob?.cancel()
            decayInputVelocity()
            // Material absorption and capsule geometry co-converge over ~185ms while active lens
            // refraction remains coupled to physical elevation (lensLift) in LiquidBottomTabs.
            launch { clarityAnimation.animateTo(0f, tween(120, easing = FastOutSlowInEasing)) }
            launch { pressProgressAnimation.animateTo(0f, releasePressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, releaseScaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, releaseScaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float, dragging: Boolean = isDragging) {
        val targetValue = value.coerceIn(valueRange)
        requestedValue = targetValue
        useTightDragSpring = dragging
        ensureValueLoop()
    }

    fun animateToValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        requestedValue = targetValue
        useTightDragSpring = false
        press()
        ensureValueLoop()
        decayInputVelocity()
        release()
    }

    private fun ensureValueLoop() {
        if (valueJob?.isActive == true) return
        val velocityThreshold = (visibilityThreshold * 62.5f).coerceAtLeast(0.01f)
        if (abs(currentValue - requestedValue) <= visibilityThreshold &&
            abs(currentValueVelocity) <= velocityThreshold
        ) {
            currentValue = requestedValue
            currentValueVelocity = 0f
            return
        }
        valueJob = animationScope.launch {
            var lastFrameNanos = Long.MIN_VALUE
            while (true) {
                val frameNanos = withFrameNanos { it }
                val dtNanos = if (lastFrameNanos == Long.MIN_VALUE) {
                    DefaultFrameStepNanos
                } else {
                    val delta = frameNanos - lastFrameNanos
                    if (delta <= 0L) DefaultFrameStepNanos else delta.coerceAtMost(MaxFrameStepNanos)
                }
                lastFrameNanos = frameNanos
                val target = requestedValue
                val spec = if (useTightDragSpring) dragValueSpec else tapValueSpec
                val nextValue = spec.getValueFromNanos(dtNanos, currentValue, target, currentValueVelocity)
                val nextVelocity = spec.getVelocityFromNanos(dtNanos, currentValue, target, currentValueVelocity)
                if (abs(nextValue - target) <= visibilityThreshold &&
                    abs(nextVelocity) <= velocityThreshold
                ) {
                    currentValue = target
                    currentValueVelocity = 0f
                    break
                }
                currentValue = nextValue
                currentValueVelocity = nextVelocity
            }
        }
    }

    private fun updateInputVelocity(slotsPerSecond: Float) {
        requestedInputVelocity = slotsPerSecond.coerceIn(-40f, 40f)
        inputVelocityDecaying = false
        inputMoveSeq++
        ensureVelocityLoop()
    }

    private fun decayInputVelocity() {
        requestedInputVelocity = 0f
        inputVelocityDecaying = true
        ensureVelocityLoop()
    }

    private fun ensureVelocityLoop() {
        if (velocityJob?.isActive == true) return
        val velocityThreshold = (visibilityThreshold * 10f).coerceAtLeast(0.01f)
        if ((inputVelocityDecaying || requestedInputVelocity == 0f) &&
            abs(currentInputVelocity) <= velocityThreshold &&
            abs(currentInputVelocityDerivative) <= InputVelocityDerivativeThreshold
        ) {
            currentInputVelocity = 0f
            currentInputVelocityDerivative = 0f
            return
        }
        velocityJob = animationScope.launch {
            var lastFrameNanos = Long.MIN_VALUE
            var observedMoveSeq = inputMoveSeq
            var idleStartNanos = Long.MIN_VALUE
            while (true) {
                val frameNanos = withFrameNanos { it }
                val dtNanos = if (lastFrameNanos == Long.MIN_VALUE) {
                    DefaultFrameStepNanos
                } else {
                    val delta = frameNanos - lastFrameNanos
                    if (delta <= 0L) DefaultFrameStepNanos else delta.coerceAtMost(MaxFrameStepNanos)
                }
                lastFrameNanos = frameNanos
                if (!inputVelocityDecaying) {
                    if (inputMoveSeq != observedMoveSeq) {
                        observedMoveSeq = inputMoveSeq
                        idleStartNanos = frameNanos
                    } else if (idleStartNanos == Long.MIN_VALUE) {
                        idleStartNanos = frameNanos
                    } else if (frameNanos - idleStartNanos >= VelocityIdleTimeoutNanos) {
                        inputVelocityDecaying = true
                        requestedInputVelocity = 0f
                    }
                }
                val target = requestedInputVelocity
                val spec = if (inputVelocityDecaying) inputVelocityDecaySpec else inputVelocityTrackSpec
                val nextVelocity = spec.getValueFromNanos(
                    dtNanos,
                    currentInputVelocity,
                    target,
                    currentInputVelocityDerivative,
                )
                val nextDerivative = spec.getVelocityFromNanos(
                    dtNanos,
                    currentInputVelocity,
                    target,
                    currentInputVelocityDerivative,
                )
                if ((inputVelocityDecaying || target == 0f) &&
                    abs(nextVelocity) <= velocityThreshold &&
                    abs(nextDerivative) <= InputVelocityDerivativeThreshold
                ) {
                    currentInputVelocity = 0f
                    currentInputVelocityDerivative = 0f
                    break
                }
                currentInputVelocity = nextVelocity
                currentInputVelocityDerivative = nextDerivative
            }
        }
    }
}

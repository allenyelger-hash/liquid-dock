/*
 * Copyright 2025 Kyant. Licensed under the Apache License, Version 2.0.
 * Adapted for liquid-dock; see THIRD_PARTY_NOTICES/AndroidLiquidGlass.md.
 */
package com.liquiddock.internal

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.shapes.Capsule
import com.liquiddock.DockGeometry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.tanh

// Non-accessibility test probes; disabled in production to avoid per-frame semantics invalidation.
internal var DockExposeAnimationSemantics: Boolean = false
internal val DockSelectedIndex = SemanticsPropertyKey<Int>("DockSelectedIndex")
internal val DockLensPressProgress = SemanticsPropertyKey<Float>("DockLensPressProgress")
internal val DockLensClarity = SemanticsPropertyKey<Float>("DockLensClarity")
internal val DockLensPosition = SemanticsPropertyKey<Float>("DockLensPosition")
internal val DockDragVelocity = SemanticsPropertyKey<Float>("DockDragVelocity")
internal val DockLensWidthScale = SemanticsPropertyKey<Float>("DockLensWidthScale")
internal val DockLensHeightScale = SemanticsPropertyKey<Float>("DockLensHeightScale")

private class BlurShadowLayer(initialAlpha: Float) {
    var blurPx: Float = -1f
    var currentAlpha: Float = initialAlpha
    val paint: Paint = Paint().apply {
        isAntiAlias = true
        color = Color.Black.copy(alpha = initialAlpha)
    }

    fun prepare(targetBlurPx: Float, targetAlpha: Float = currentAlpha) {
        if (abs(targetBlurPx - blurPx) > 0.05f) {
            blurPx = targetBlurPx
            paint.asFrameworkPaint().maskFilter =
                BlurMaskFilter(targetBlurPx, BlurMaskFilter.Blur.NORMAL)
        }
        if (abs(targetAlpha - currentAlpha) > 0.0005f) {
            currentAlpha = targetAlpha
            paint.color = Color.Black.copy(alpha = targetAlpha.coerceIn(0f, 1f))
        }
    }
}

/**
 * Shared 3-layer physical floating shadow cache for [LiquidBottomTabs] base capsule and [LiquidButton].
 * Keeps all blurred draws outside `recordedBase` and masked by `ClipOp.Difference` so 0% shadow
 * darkness enters the glass interior.
 */
internal class DockFloatingShadowCache {
    private val ambientHalo = BlurShadowLayer(0.14f)
    private val midCushion = BlurShadowLayer(0.12f)
    private val contactOcclusion = BlurShadowLayer(0.10f)
    private var pathCx: Float = 0f
    private var pathCy: Float = 0f
    private var pathW: Float = -1f
    private var pathH: Float = -1f
    private val bodyPath: Path = Path()

    fun draw(
        scope: DrawScope,
        cx: Float,
        bodyCy: Float,
        w: Float,
        h: Float,
    ) {
        if (w <= 0f || h <= 0f) return
        val halfW = w * 0.5f
        val halfH = h * 0.5f
        val r = min(w, h) * 0.5f
        if (
            abs(cx - pathCx) > 0.01f ||
            abs(bodyCy - pathCy) > 0.01f ||
            abs(w - pathW) > 0.01f ||
            abs(h - pathH) > 0.01f
        ) {
            pathCx = cx
            pathCy = bodyCy
            pathW = w
            pathH = h
            bodyPath.reset()
            bodyPath.addRoundRect(
                RoundRect(
                    left = cx - halfW,
                    top = bodyCy - halfH,
                    right = cx + halfW,
                    bottom = bodyCy + halfH,
                    cornerRadius = CornerRadius(r, r),
                ),
            )
        }
        ambientHalo.prepare((h * 0.195f).coerceAtLeast(1f))
        midCushion.prepare((h * 0.062f).coerceAtLeast(1f))
        contactOcclusion.prepare((h * 0.020f).coerceAtLeast(0.8f))

        scope.clipPath(bodyPath, ClipOp.Difference) {
            val canvas = drawContext.canvas
            val left = cx - halfW
            val right = cx + halfW

            val ambientCy = bodyCy + h * 0.075f
            canvas.drawRoundRect(
                left = left,
                top = ambientCy - halfH,
                right = right,
                bottom = ambientCy + halfH,
                radiusX = r,
                radiusY = r,
                paint = ambientHalo.paint,
            )

            val midCy = bodyCy + h * 0.026f
            canvas.drawRoundRect(
                left = left,
                top = midCy - halfH,
                right = right,
                bottom = midCy + halfH,
                radiusX = r,
                radiusY = r,
                paint = midCushion.paint,
            )

            val contactCy = bodyCy + h * 0.009f
            canvas.drawRoundRect(
                left = left,
                top = contactCy - halfH,
                right = right,
                bottom = contactCy + halfH,
                radiusX = r,
                radiusY = r,
                paint = contactOcclusion.paint,
            )
        }
    }
}

/**
 * Two-layer physical elevation shadow cache for the foreground lifted 3D droplet lens in [LiquidBottomTabs].
 * Sits outside `recordedBase` and is masked by `ClipOp.Difference` so fading `dockLensOpacity(lensLift)`
 * never shows interior darkness.
 */
internal class DockLensShadowCache {
    private val elevationHalo = BlurShadowLayer(0f)
    private val castContact = BlurShadowLayer(0f)
    private var pathCx: Float = 0f
    private var pathCy: Float = 0f
    private var pathW: Float = -1f
    private var pathH: Float = -1f
    private val lensBodyPath: Path = Path()

    fun draw(
        scope: DrawScope,
        cx: Float,
        bodyCy: Float,
        w: Float,
        h: Float,
        baseHeightPx: Float,
        liftFactor: Float,
    ) {
        if (w <= 0f || h <= 0f || baseHeightPx <= 0f || liftFactor <= 0.01f) return
        val halfW = w * 0.5f
        val halfH = h * 0.5f
        val r = min(w, h) * 0.5f
        if (
            abs(cx - pathCx) > 0.01f ||
            abs(bodyCy - pathCy) > 0.01f ||
            abs(w - pathW) > 0.01f ||
            abs(h - pathH) > 0.01f
        ) {
            pathCx = cx
            pathCy = bodyCy
            pathW = w
            pathH = h
            lensBodyPath.reset()
            lensBodyPath.addRoundRect(
                RoundRect(
                    left = cx - halfW,
                    top = bodyCy - halfH,
                    right = cx + halfW,
                    bottom = bodyCy + halfH,
                    cornerRadius = CornerRadius(r, r),
                ),
            )
        }
        elevationHalo.prepare(
            targetBlurPx = (baseHeightPx * 0.155f).coerceAtLeast(1f),
            targetAlpha = 0.09f * liftFactor,
        )
        castContact.prepare(
            targetBlurPx = (baseHeightPx * 0.052f).coerceAtLeast(1f),
            targetAlpha = 0.12f * liftFactor,
        )

        scope.clipPath(lensBodyPath, ClipOp.Difference) {
            val canvas = drawContext.canvas
            val left = cx - halfW
            val right = cx + halfW

            val haloCy = bodyCy + baseHeightPx * 0.072f
            canvas.drawRoundRect(
                left = left,
                top = haloCy - halfH,
                right = right,
                bottom = haloCy + halfH,
                radiusX = r,
                radiusY = r,
                paint = elevationHalo.paint,
            )

            val contactCy = bodyCy + baseHeightPx * 0.030f
            canvas.drawRoundRect(
                left = left,
                top = contactCy - halfH,
                right = right,
                bottom = contactCy + halfH,
                radiusX = r,
                radiusY = r,
                paint = castContact.paint,
            )
        }
    }
}

/** Two clean 1:1 recorded layers (base glass & transparent labels, zero external shadow pollution); foreground lens sits outside. */
@Composable
internal fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    geometry: DockGeometry,
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    require(tabsCount > 0)
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val tabWidth = with(density) { geometry.slotWidth(tabsCount).toPx() }
    val restWidthRatio = geometry.restWidthRatio(tabsCount)
    val inset = with(density) { geometry.listInsetH.toPx() }
    val bodyWidth = with(density) { geometry.bodyWidth.toPx() }
    val maxPanelShiftPx = with(density) { geometry.maxPanelShift.toPx() }
    val scope = rememberCoroutineScope()
    val latestIndex by rememberUpdatedState(selectedTabIndex())
    val latestSelected by rememberUpdatedState(onTabSelected)
    val latestWidth by rememberUpdatedState(tabWidth)
    val latestLtr by rememberUpdatedState(isLtr)
    val latestInset by rememberUpdatedState(inset)
    val latestBodyWidth by rememberUpdatedState(bodyWidth)
    val latestMaxPanelShiftPx by rememberUpdatedState(maxPanelShiftPx)
    var dragPullPx by remember { mutableFloatStateOf(0f) }
    var panelOffsetJob by remember { mutableStateOf<Job?>(null) }
    val panelOffsetAnimation = remember { Animatable(0f) }

    fun releasePanelOffset() {
        dragPullPx = 0f
        panelOffsetJob?.cancel()
        panelOffsetJob = scope.launch {
            panelOffsetAnimation.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 320f))
        }
    }

    val controller = remember(scope, tabsCount, geometry.pillHeight, geometry.pressedPillHeight) {
        createDockDragAnimation(
            scope = scope,
            tabsCount = tabsCount,
            pressedScale = geometry.pressedPillHeight / geometry.pillHeight,
            selectedTabIndex = { latestIndex },
            onTabSelected = { latestSelected(it) },
            tabWidthPx = { latestWidth },
            insetPx = { latestInset },
            bodyWidthPx = { latestBodyWidth },
            isLtr = { latestLtr },
            onGestureStarted = {
                dragPullPx = 0f
                panelOffsetJob?.cancel()
            },
            onHorizontalDragDelta = { deltaX ->
                dragPullPx += deltaX
                val shift = latestMaxPanelShiftPx * tanh(dragPullPx / (latestBodyWidth * 0.45f).coerceAtLeast(1f))
                panelOffsetJob?.cancel()
                panelOffsetJob = scope.launch { panelOffsetAnimation.snapTo(shift) }
            },
            onGestureEnded = { releasePanelOffset() },
        )
    }
    val animation = controller.animation
    DisposableEffect(controller) {
        onDispose { controller.cancel() }
    }
    LaunchedEffect(controller) {
        dragPullPx = 0f
        panelOffsetJob?.cancel()
        panelOffsetJob = null
        panelOffsetAnimation.snapTo(0f)
    }
    LaunchedEffect(latestIndex, controller) {
        controller.onSelectionObserved(latestIndex)
        if (!animation.isPressed && animation.targetValue != latestIndex.toFloat()) {
            animation.animateToValue(latestIndex.toFloat())
        }
    }
    val recordedBase = rememberLayerBackdrop()
    val recordedLabels = rememberLayerBackdrop()
    val lensBackdrop = rememberCombinedBackdrop(backdrop, recordedBase)
    val baseRim = remember { dockRimHighlight() }
    val shadowCache = remember { DockFloatingShadowCache() }
    val lensShadowCache = remember { DockLensShadowCache() }

    fun unscaledCenterX(): Float =
        if (isLtr) inset + (animation.value + 0.5f) * tabWidth
        else bodyWidth - inset - (animation.value + 0.5f) * tabWidth

    fun centerX(): Float {
        val sx = 1f + geometry.baseExpansionX * animation.pressProgress.coerceAtLeast(0f)
        return bodyWidth / 2f + (unscaledCenterX() - bodyWidth / 2f) * sx + panelOffsetAnimation.value
    }

    // This envelope is also used for page avoidance and AnimatedVisibility's offscreen bounds.
    // zIndex(1f) keeps the lifted last-slot foreground droplet above sibling FAB's ground shadow in Row.
    Box(modifier.zIndex(1f).height(geometry.layoutHeight), contentAlignment = Alignment.Center) {
        Box(
            Modifier.height(geometry.height).fillMaxWidth()
                .testTag("dock-tabs")
                .then(
                    if (DockExposeAnimationSemantics) {
                        Modifier.semantics {
                            this[DockSelectedIndex] = latestIndex
                            this[DockLensPressProgress] = animation.pressProgress
                            this[DockLensClarity] = animation.clarity
                            this[DockLensPosition] = animation.value
                            this[DockDragVelocity] = animation.velocity
                            this[DockLensWidthScale] = dockLensScaleX(animation.scaleX, animation.velocity, geometry, tabsCount)
                            this[DockLensHeightScale] = dockLensScaleY(animation.scaleY, animation.velocity, geometry)
                        }
                    } else {
                        Modifier
                    },
                )
                .then(animation.modifier),
            contentAlignment = AbsoluteAlignment.CenterLeft,
        ) {
            // Outer unscaled 1:1 recording container (no scaleX/scaleY!) so LayerBackdrop.drawBackdrop
            // replays recordedBase and recordedLabels at exact 1:1 screen scale without right/bottom drift,
            // while keeping the multi-layer shadow strictly BEFORE .layerBackdrop(recordedBase) (0.0% shadow pollution).
            Box(
                Modifier.requiredSize(
                    geometry.bodyWidth * 1.16f + geometry.maxPanelShift * 2,
                    geometry.layoutHeight,
                ),
                contentAlignment = Alignment.Center,
            ) {
                // Layer 1: Base frosted glass capsule ONLY (no tab glyphs/badges) so the foreground lens
                // refracts page backdrop + dock base with unbroken 360-deg concentric meniscus and zero endcap adhesion.
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            val p = animation.pressProgress.coerceAtLeast(0f)
                            val sx = 1f + geometry.baseExpansionX * p
                            val sy = 1f + geometry.baseExpansionY * p
                            shadowCache.draw(
                                scope = this,
                                cx = size.width * 0.5f + panelOffsetAnimation.value,
                                bodyCy = size.height * 0.5f,
                                w = bodyWidth * sx,
                                h = geometry.height.toPx() * sy,
                            )
                        }
                        .layerBackdrop(recordedBase),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.width(geometry.bodyWidth).height(geometry.height)
                            .graphicsLayer {
                                val p = animation.pressProgress.coerceAtLeast(0f)
                                translationX = panelOffsetAnimation.value
                                scaleX = 1f + geometry.baseExpansionX * p
                                scaleY = 1f + geometry.baseExpansionY * p
                            }
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { Capsule() },
                                effects = {
                                    val p = animation.pressProgress.coerceAtLeast(0f)
                                    val sx = 1f + geometry.baseExpansionX * p
                                    val sy = 1f + geometry.baseExpansionY * p
                                    val lensScaleX = dockLensScaleX(animation.scaleX, animation.velocity, geometry, tabsCount)
                                    val lensScaleY = dockLensScaleY(animation.scaleY, animation.velocity, geometry)
                                    val lensLift = dockLensLift(lensScaleY, p, geometry)
                                    val fpHalfW = tabWidth * restWidthRatio * lensScaleX / (2f * sx)
                                    val fpHalfH = geometry.pillHeight.toPx() * lensScaleY / (2f * sy)
                                    dockBaseGlass(
                                        pressProgress = p,
                                        lensLift = lensLift,
                                        clarity = animation.clarity,
                                        pillCenter = Offset(unscaledCenterX() - size.width / 2f, 0f),
                                        pillHalfSize = Offset(tabWidth * restWidthRatio / 2f, geometry.pillHeight.toPx() / 2f),
                                        footprintCenter = Offset(unscaledCenterX() - size.width / 2f, 0f),
                                        footprintHalfSize = Offset(fpHalfW, fpHalfH),
                                    )
                                },
                                highlight = { baseRim },
                                shadow = null,
                                innerShadow = null,
                                onDrawSurface = {
                                    if (Build.VERSION.SDK_INT < 33) {
                                        drawRect(containerColor)
                                        if (Build.VERSION.SDK_INT >= 31) drawRect(Color.White.copy(alpha = 0.08f))
                                    }
                                },
                            )
                            .drawWithContent {
                                // API 33+ renders the per-pixel luminance-adaptive resting dark capsule inside dockBaseGlass;
                                // API 26-32 has no RuntimeShader foreground lens (dockLens early-returns), so keep the dark
                                // selection pill visible and highlighted throughout press & cross-tab drag instead of vanishing.
                                if (Build.VERSION.SDK_INT < 33) {
                                    val press = animation.pressProgress.coerceIn(0f, 1f)
                                    val strokePx = 1.dp.toPx()
                                    val maxPillWidthPx = (size.width - strokePx).coerceAtLeast(tabWidth)
                                    val width = (tabWidth * restWidthRatio * lerp(1f, 1.06f, press)).coerceAtMost(maxPillWidthPx)
                                    val height = geometry.pillHeight.toPx()
                                    val localCenterX = unscaledCenterX()
                                    val minLeft = strokePx * 0.5f
                                    val maxLeft = (size.width - width - strokePx * 0.5f).coerceAtLeast(minLeft)
                                    val topLeft = Offset((localCenterX - width / 2f).coerceIn(minLeft, maxLeft), (size.height - height) / 2f)
                                    val pillSize = Size(width, height)
                                    val corner = CornerRadius(height / 2f)
                                    drawRoundRect(
                                        Color.Black.copy(alpha = lerp(0.28f, 0.36f, press)),
                                        topLeft = topLeft,
                                        size = pillSize,
                                        cornerRadius = corner,
                                    )
                                    if (press > 0.01f) {
                                        drawRoundRect(
                                            Color.White.copy(alpha = 0.22f * press),
                                            topLeft = topLeft,
                                            size = pillSize,
                                            cornerRadius = corner,
                                            style = Stroke(width = strokePx),
                                        )
                                    }
                                }
                                drawContent()
                            },
                    )
                }

                // Layer 2: Transparent tab labels & badges ONLY at 1:1 scale so the foreground droplet lens
                // refracts and disperses swept glyphs/badges independently over the refracted base bar.
                Box(
                    Modifier
                        .matchParentSize()
                        .layerBackdrop(recordedLabels),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier.width(geometry.bodyWidth).height(geometry.height)
                            .graphicsLayer {
                                val p = animation.pressProgress.coerceAtLeast(0f)
                                translationX = panelOffsetAnimation.value
                                scaleX = 1f + geometry.baseExpansionX * p
                                scaleY = 1f + geometry.baseExpansionY * p
                            }
                            .selectableGroup()
                            .padding(horizontal = geometry.listInsetH, vertical = geometry.pillInsetY),
                        verticalAlignment = Alignment.CenterVertically,
                        content = content,
                    )
                }
            }
            // Its own G2 continuous capsule renders the lifted 3D water-droplet lens in two aligned passes:
            // (1) backdrop + base glass (360-deg concentric meniscus + 4-corner Cauchy prism);
            // (2) transparent tab labels & badges (360-deg convex droplet stretch + dispersion),
            // grouped under one alpha layer so release handoff dissolves cleanly without brightness dip.
            Box(
                Modifier
                    .absoluteOffset {
                        IntOffset(
                            (centerX() - tabWidth * restWidthRatio *
                                dockLensScaleX(animation.scaleX, animation.velocity, geometry, tabsCount) / 2f).fastRoundToInt(),
                            0,
                        )
                    }
                    .requiredSize(
                        geometry.slotWidth(tabsCount) * restWidthRatio *
                            dockLensScaleX(animation.scaleX, animation.velocity, geometry, tabsCount),
                        geometry.pillHeight * dockLensScaleY(animation.scaleY, animation.velocity, geometry),
                    )
                    .drawBehind {
                        val lensScaleY = dockLensScaleY(animation.scaleY, animation.velocity, geometry)
                        val lensLift = dockLensLift(lensScaleY, animation.pressProgress, geometry)
                        val liftFactor = lensLift.coerceIn(0f, 1f) * dockLensOpacity(lensLift)
                        if (liftFactor > 0.01f) {
                            lensShadowCache.draw(
                                scope = this,
                                cx = size.width * 0.5f,
                                bodyCy = size.height * 0.5f,
                                w = size.width,
                                h = size.height,
                                baseHeightPx = geometry.height.toPx(),
                                liftFactor = liftFactor,
                            )
                        }
                    },
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            // Dissolve only after the shrinking capsule is well inside the base bar and refraction is subpixel.
                            val lensLift = dockLensLift(
                                dockLensScaleY(animation.scaleY, animation.velocity, geometry),
                                animation.pressProgress,
                                geometry,
                            )
                            alpha = dockLensOpacity(lensLift)
                        },
                ) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .drawPlainBackdrop(
                                backdrop = lensBackdrop,
                                shape = { Capsule() },
                                effects = {
                                    val lensLift = dockLensLift(
                                        dockLensScaleY(animation.scaleY, animation.velocity, geometry),
                                        animation.pressProgress,
                                        geometry,
                                    )
                                    val p = animation.pressProgress.coerceAtLeast(0f)
                                    val sx = 1f + geometry.baseExpansionX * p
                                    val sy = 1f + geometry.baseExpansionY * p
                                    val baseCenterInLens = Offset((bodyWidth * 0.5f + panelOffsetAnimation.value) - centerX(), 0f)
                                    val baseHalfSizePx = Offset(bodyWidth * sx * 0.5f, geometry.height.toPx() * sy * 0.5f)
                                    dockLens(
                                        progress = lensLift,
                                        baseCenter = baseCenterInLens,
                                        baseHalfSize = baseHalfSizePx,
                                    )
                                },
                            ),
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .drawPlainBackdrop(
                                backdrop = recordedLabels,
                                shape = { Capsule() },
                                effects = {
                                    val lensLift = dockLensLift(
                                        dockLensScaleY(animation.scaleY, animation.velocity, geometry),
                                        animation.pressProgress,
                                        geometry,
                                    )
                                    dockLabelLens(progress = lensLift)
                                },
                            ),
                    )
                }
            }
        }
    }
}

internal const val DockHoldDwellNanos = 95_000_000L

internal fun dockContinuousSlot(
    x: Float,
    insetPx: Float,
    tabWidthPx: Float,
    tabsCount: Int,
): Float = ((x - insetPx) / tabWidthPx.coerceAtLeast(1f) - 0.5f)
    .coerceIn(0f, (tabsCount - 1).toFloat())

internal class DockDragController(
    val animation: DampedDragAnimation,
    val onSelectionObserved: (Int) -> Unit,
    val cancel: () -> Unit,
)

internal fun createDockDragAnimation(
    scope: CoroutineScope,
    tabsCount: Int,
    pressedScale: Float,
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    tabWidthPx: () -> Float,
    insetPx: () -> Float,
    bodyWidthPx: () -> Float,
    isLtr: () -> Boolean = { true },
    onGestureStarted: () -> Unit = {},
    onHorizontalDragDelta: (Float) -> Unit = {},
    onGestureEnded: () -> Unit = {},
): DockDragController {
    val initialSelected = selectedTabIndex().coerceIn(0, (tabsCount - 1).coerceAtLeast(0))
    var acceptedIndex = initialSelected
    var inFlightDispatchedIndex = -1
    var externalOverride = false
    var pointerX = 0f
    var dwellJob: Job? = null
    var dwellCandidate = -1
    lateinit var animation: DampedDragAnimation

    fun cancelDwell() {
        dwellJob?.cancel()
        dwellJob = null
        dwellCandidate = -1
    }

    fun observeSelection(currentSelected: Int): Boolean {
        if (!externalOverride) {
            if (inFlightDispatchedIndex != -1 && currentSelected == inFlightDispatchedIndex) {
                acceptedIndex = inFlightDispatchedIndex
                inFlightDispatchedIndex = -1
            } else if (currentSelected != acceptedIndex && currentSelected != inFlightDispatchedIndex) {
                externalOverride = true
                cancelDwell()
            }
        }
        return externalOverride
    }

    fun effectiveSelectedIndex(): Int =
        if (inFlightDispatchedIndex != -1) inFlightDispatchedIndex else acceptedIndex

    fun dispatchSelectionFromGesture(index: Int) {
        val currentSelected = selectedTabIndex()
        if (observeSelection(currentSelected)) return
        if (index == effectiveSelectedIndex()) return
        acceptedIndex = currentSelected
        inFlightDispatchedIndex = index
        onTabSelected(index)
        if (selectedTabIndex() == index) {
            acceptedIndex = index
            inFlightDispatchedIndex = -1
        }
    }

    fun updateDwellForTarget(anim: DampedDragAnimation) {
        if (!anim.isPressed) {
            cancelDwell()
            return
        }
        if (observeSelection(selectedTabIndex())) {
            cancelDwell()
            return
        }
        val candidate = dockTargetIndex(anim.targetValue, tabsCount)
        if (candidate == effectiveSelectedIndex()) {
            cancelDwell()
            return
        }
        if (dwellJob?.isActive == true && dwellCandidate == candidate) {
            return
        }
        cancelDwell()
        dwellCandidate = candidate
        dwellJob = scope.launch {
            val startNanos = withFrameNanos { it }
            var nowNanos = startNanos
            while (nowNanos - startNanos < DockHoldDwellNanos) {
                nowNanos = withFrameNanos { it }
            }
            if (!anim.isPressed) return@launch
            if (observeSelection(selectedTabIndex())) return@launch
            val settledCandidate = dockTargetIndex(anim.targetValue, tabsCount)
            if (settledCandidate == candidate && settledCandidate != effectiveSelectedIndex()) {
                dispatchSelectionFromGesture(settledCandidate)
            }
        }
    }

    animation = DampedDragAnimation(
        animationScope = scope,
        initialValue = initialSelected.toFloat(),
        valueRange = 0f..(tabsCount - 1).toFloat(),
        visibilityThreshold = 0.001f,
        initialScale = 1f,
        pressedScale = pressedScale,
        onDragStarted = { position ->
            val currentSelected = selectedTabIndex()
            acceptedIndex = currentSelected
            inFlightDispatchedIndex = -1
            externalOverride = false
            pointerX = position.x
            cancelDwell()
            onGestureStarted()
            val x = if (isLtr()) position.x else bodyWidthPx() - position.x
            val continuousSlot = dockContinuousSlot(x, insetPx(), tabWidthPx(), tabsCount)
            val pressedSlot = dockTargetIndex(continuousSlot, tabsCount)
            // Keep target centered when pressing within the active tab to prevent an off-center twitch;
            // pressing a different tab glides directly toward the finger coordinate and commits on UP
            // unless actual horizontal drag (delta.x != 0f) arms held-drag live switching.
            updateValue(if (pressedSlot == currentSelected) currentSelected.toFloat() else continuousSlot)
        },
        onDragStopped = {
            cancelDwell()
            val overridden = observeSelection(selectedTabIndex())
            val currentSelected = selectedTabIndex()
            val index = if (overridden) {
                currentSelected
            } else {
                dockTargetIndex(targetValue, tabsCount)
            }
            updateValue(index.toFloat())
            onGestureEnded()
            if (!overridden && index != effectiveSelectedIndex()) {
                dispatchSelectionFromGesture(index)
            }
        },
        onDragCancelled = {
            cancelDwell()
            val settledSelected = if (!externalOverride && inFlightDispatchedIndex != -1) {
                inFlightDispatchedIndex
            } else {
                selectedTabIndex()
            }
            acceptedIndex = settledSelected
            inFlightDispatchedIndex = -1
            updateValue(settledSelected.toFloat())
            onGestureEnded()
        },
        onDrag = { _, delta ->
            observeSelection(selectedTabIndex())
            if (delta.x != 0f) {
                pointerX += delta.x
                val x = if (isLtr()) pointerX else bodyWidthPx() - pointerX
                val continuousSlot = dockContinuousSlot(x, insetPx(), tabWidthPx(), tabsCount)
                updateValue(continuousSlot)
                onHorizontalDragDelta(delta.x)
                updateDwellForTarget(this)
            }
        },
    )

    val onSelectionObserved: (Int) -> Unit = { currentSelected ->
        if (animation.isPressed) {
            observeSelection(currentSelected)
        } else {
            acceptedIndex = currentSelected
            inFlightDispatchedIndex = -1
            externalOverride = false
        }
    }
    val cancelAll: () -> Unit = {
        cancelDwell()
        animation.cancel()
    }
    return DockDragController(
        animation = animation,
        onSelectionObserved = onSelectionObserved,
        cancel = cancelAll,
    )
}

internal fun dockTargetIndex(value: Float, tabsCount: Int): Int =
    value.fastRoundToInt().coerceIn(0, tabsCount - 1)

internal fun dockLensLift(lensScaleY: Float, pressProgress: Float, geometry: DockGeometry): Float {
    val fullRise = (geometry.pressedPillHeight / geometry.pillHeight - 1f).coerceAtLeast(0.05f)
    val geomLift = ((lensScaleY - 1f) / fullRise).coerceIn(0f, 1.15f)
    return (0.78f * geomLift + 0.22f * pressProgress.coerceAtLeast(0f)).coerceIn(0f, 1.15f)
}

internal fun dockLensOpacity(lensLift: Float): Float = (lensLift / 0.14f).coerceIn(0f, 1f)

internal fun dockLensScaleX(
    scale: Float,
    velocity: Float,
    geometry: DockGeometry,
    tabsCount: Int = 4,
): Float {
    val restRatio = geometry.restWidthRatio(tabsCount)
    val pressedRatio = geometry.pressedWidthRatio(tabsCount)
    val rise = ((scale - 1f) / (geometry.pressedPillHeight / geometry.pillHeight - 1f)).coerceIn(-0.08f, 1.15f)
    val speed = (abs(velocity) / 12f).coerceIn(0f, 1f)
    return (lerp(1f, pressedRatio / restRatio, rise) *
        (1f + speed * geometry.maxVelocityStretch(tabsCount)))
        // Round down before converting back to Dp so a multiply/divide round trip cannot
        // exceed the geometry's capture envelope by one floating-point ULP.
        .coerceIn(0.95f, Math.nextDown(geometry.maxPillWidth(tabsCount) / (geometry.slotWidth(tabsCount) * restRatio)))
}

internal fun dockLensScaleY(scale: Float, velocity: Float, geometry: DockGeometry): Float {
    val speed = (abs(velocity) / 12f).coerceIn(0f, 1f)
    return (scale * (1f - speed * 0.118f)).coerceIn(0.95f, geometry.maxPillHeight / geometry.pillHeight)
}

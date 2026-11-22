/*
 * Copyright 2025 Kyant. Licensed under the Apache License, Version 2.0.
 * Adapted for liquid-dock; see THIRD_PARTY_NOTICES/AndroidLiquidGlass.md.
 */
package com.liquiddock.internal

import android.os.Build
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.shapes.Capsule
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
internal fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit,
) {
    val animationScope = rememberCoroutineScope()
    val rim = remember { dockRimHighlight() }
    val shadowCache = remember { DockFloatingShadowCache() }

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
        )
    }

    Row(
        modifier
            .drawBehind {
                val width = size.width
                val height = size.height
                if (width <= 0f || height <= 0f) return@drawBehind
                if (isInteractive) {
                    val progress = interactiveHighlight.pressProgress
                    val maxDragScale = 4f.dp.toPx() / height
                    val scale = lerp(1f, 1f + maxDragScale, progress)

                    val maxOffset = size.minDimension
                    val initialDerivative = 0.05f
                    val offset = interactiveHighlight.offset
                    val tx = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                    val ty = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                    val maxDim = size.maxDimension
                    val offsetAngle = atan2(offset.y, offset.x)
                    val sx =
                        scale +
                            maxDragScale * abs(cos(offsetAngle) * offset.x / maxDim) *
                            (width / height).fastCoerceAtMost(1f)
                    val sy =
                        scale +
                            maxDragScale * abs(sin(offsetAngle) * offset.y / maxDim) *
                            (height / width).fastCoerceAtMost(1f)
                    shadowCache.draw(
                        scope = this,
                        cx = width * 0.5f + tx,
                        bodyCy = height * 0.5f + ty,
                        w = width * sx,
                        h = height * sy,
                    )
                } else {
                    shadowCache.draw(
                        scope = this,
                        cx = width * 0.5f,
                        bodyCy = height * 0.5f,
                        w = width,
                        h = height,
                    )
                }
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    dockBaseGlass(pressProgress = interactiveHighlight.pressProgress * 0.6f)
                },
                highlight = { rim },
                shadow = null,
                innerShadow = null,
                layerBlock = if (isInteractive) {
                    {
                        val width = size.width
                        val height = size.height
                        if (width > 0f && height > 0f) {
                            val progress = interactiveHighlight.pressProgress
                            val maxDragScale = 4f.dp.toPx() / height
                            val scale = lerp(1f, 1f + maxDragScale, progress)

                            val maxOffset = size.minDimension
                            val initialDerivative = 0.05f
                            val offset = interactiveHighlight.offset
                            translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                            translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                            val maxDim = size.maxDimension
                            val offsetAngle = atan2(offset.y, offset.x)
                            scaleX =
                                scale +
                                    maxDragScale * abs(cos(offsetAngle) * offset.x / maxDim) *
                                    (width / height).fastCoerceAtMost(1f)
                            scaleY =
                                scale +
                                    maxDragScale * abs(sin(offsetAngle) * offset.y / maxDim) *
                                    (height / width).fastCoerceAtMost(1f)
                        }
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                    if (surfaceColor.isSpecified && Build.VERSION.SDK_INT < 33) {
                        drawRect(surfaceColor)
                        if (Build.VERSION.SDK_INT >= 31) drawRect(Color.White.copy(alpha = 0.08f))
                    }
                },
            )
            .clickable(
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .then(
                if (isInteractive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(8f.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

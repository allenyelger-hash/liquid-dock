/*
 * Copyright 2025-2026 Liquid Dock Contributors.
 * Portions adapted from Kyant0/AndroidLiquidGlass (Copyright 2025 Kyant).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.liquiddock

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.liquiddock.internal.LiquidBottomTab
import com.liquiddock.internal.LiquidBottomTabs
import com.liquiddock.internal.LiquidButton

/**
 * A single destination tab in [LiquidGlassDock].
 *
 * @param label Short label shown inside the tab slot.
 * @param badgeCount Optional positive count shown as a circular pill badge (`99+` when > 99).
 */
@Immutable
public data class DockTab(
    public val label: String,
    public val badgeCount: Int = 0,
)

/**
 * Color configuration for [LiquidGlassDock] and [LiquidDockHost].
 *
 * Note: [blurFallbackColor] and [legacyFallbackColor] govern the translucent/opaque container
 * fallback surfaces on API 31–32 and API 26–30 respectively; API 33+ uses adaptive shader
 * transmission over the live [Backdrop].
 */
@Immutable
public data class LiquidDockColors(
    public val contentColor: Color,
    public val badgeContainerColor: Color,
    public val badgeContentColor: Color,
    public val blurFallbackColor: Color,
    public val legacyFallbackColor: Color,
) {
    internal fun resolvedContainerColor(): Color = if (Build.VERSION.SDK_INT >= 31) {
        blurFallbackColor
    } else {
        legacyFallbackColor
    }
}

/**
 * Default styling tokens for [LiquidGlassDock].
 */
public object LiquidDockDefaults {
    public val BadgeRed: Color = Color(0xFFFE2C55)
    public val LegacyFallback: Color = Color(0xFF373737).copy(alpha = 0.92f)
    public val BlurFallback: Color = Color.Black.copy(alpha = 0.24f)

    public fun colors(
        contentColor: Color = Color.White,
        badgeContainerColor: Color = BadgeRed,
        badgeContentColor: Color = Color.White,
        blurFallbackColor: Color = BlurFallback,
        legacyFallbackColor: Color = LegacyFallback,
    ): LiquidDockColors = LiquidDockColors(
        contentColor = contentColor,
        badgeContainerColor = badgeContainerColor,
        badgeContentColor = badgeContentColor,
        blurFallbackColor = blurFallbackColor,
        legacyFallbackColor = legacyFallbackColor,
    )
}

/**
 * Out-of-the-box container that records its [content] into a live [Backdrop] and overlays
 * [LiquidGlassDock] at the bottom edge with automatic window-insets handling.
 *
 * Pass [dockClearance] to scrollable content's bottom padding so items can scroll cleanly
 * above the floating glass bar and lifted droplet lens.
 *
 * For advanced multi-layer or custom fallback backdrops, call [LiquidGlassDock] directly.
 */
@Composable
public fun LiquidDockHost(
    tabs: List<DockTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: LiquidDockColors = LiquidDockDefaults.colors(),
    contentWindowInsets: WindowInsets = WindowInsets.navigationBars,
    actionIcon: ImageVector? = null,
    actionContentDescription: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable BoxScope.(dockClearance: Dp) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val navBarBottom = contentWindowInsets.asPaddingValues().calculateBottomPadding()
        val clearance = dockClear(maxWidth) + navBarBottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentWindowInsets)
                .layerBackdrop(backdrop),
        ) {
            content(clearance)
        }
        LiquidGlassDock(
            backdrop = backdrop,
            tabs = tabs,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            colors = colors,
            actionIcon = actionIcon,
            actionContentDescription = actionContentDescription,
            onAction = onAction,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(contentWindowInsets)
                .padding(bottom = DockBottomGap),
        )
    }
}

/**
 * Floating liquid-glass bottom dock with a continuous frosted capsule base, a 3D overhanging
 * droplet lens on press/drag (API 33+ RuntimeShader with API 26–32 fallbacks), and an optional
 * detached circular action button.
 *
 * Supports 2 to 5 tabs. When providing a detached action button, [actionIcon] and [onAction]
 * must both be non-null (or both null to omit it).
 */
@Composable
public fun LiquidGlassDock(
    backdrop: Backdrop,
    tabs: List<DockTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: LiquidDockColors = LiquidDockDefaults.colors(),
    actionIcon: ImageVector? = null,
    actionContentDescription: String? = null,
    onAction: (() -> Unit)? = null,
) {
    require(tabs.size in 2..5) { "LiquidGlassDock requires 2 to 5 tabs, but got ${tabs.size}." }
    require((actionIcon == null) == (onAction == null)) {
        "actionIcon and onAction must both be provided or both be null."
    }
    val safeSelectedIndex = selectedIndex.coerceIn(0, tabs.lastIndex)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val geometry = dockGeometry(maxWidth)
        val density = LocalDensity.current
        val foreground = colors.contentColor
        val labelFontSize = with(density) { (geometry.height * 0.228f).toSp() }
        val badgeFontSize = with(density) { (geometry.height * 0.18f).toSp() }
        val badgeLineHeight = with(density) { (geometry.height * 0.215f).toSp() }
        val containerFallbackColor = colors.resolvedContainerColor()
        // Subtle ambient halo keeps white labels crisp when the raised lens crosses pure light pages,
        // while staying virtually imperceptible over dark/colorful feeds.
        val labelStyle = LocalTextStyle.current.copy(
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.16f),
                offset = Offset(0f, 0.5f),
                blurRadius = 2.2f,
            ),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(geometry.actionGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidBottomTabs(
                selectedTabIndex = { safeSelectedIndex },
                onTabSelected = onSelect,
                backdrop = backdrop,
                tabsCount = tabs.size,
                geometry = geometry,
                containerColor = containerFallbackColor,
                modifier = Modifier.width(geometry.bodyWidth),
            ) {
                tabs.forEachIndexed { index, tab ->
                    val badgeLabel = if (tab.badgeCount > 99) "99+" else tab.badgeCount.toString()
                    val tabModifier = if (tab.badgeCount > 0) {
                        Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "${tab.label}, $badgeLabel"
                        }
                    } else {
                        Modifier
                    }
                    LiquidBottomTab(
                        selected = safeSelectedIndex == index,
                        onClick = { if (selectedIndex != index) onSelect(index) },
                        modifier = tabModifier,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = tab.label,
                                color = foreground,
                                fontSize = labelFontSize,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = labelStyle,
                            )
                            if (tab.badgeCount > 0) {
                                Text(
                                    text = badgeLabel,
                                    color = colors.badgeContentColor,
                                    fontSize = badgeFontSize,
                                    lineHeight = badgeLineHeight,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = geometry.height * 0.24f, y = -geometry.height * 0.145f)
                                        .defaultMinSize(minWidth = geometry.height * 0.295f)
                                        .background(colors.badgeContainerColor, CircleShape)
                                        .padding(horizontal = geometry.height * 0.062f, vertical = geometry.height * 0.018f),
                                )
                            }
                        }
                    }
                }
            }
            if (actionIcon != null && onAction != null) {
                LiquidButton(
                    onClick = onAction,
                    backdrop = backdrop,
                    modifier = Modifier.size(geometry.fabSize),
                    surfaceColor = containerFallbackColor,
                ) {
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = actionContentDescription,
                        tint = foreground,
                        modifier = Modifier.size(geometry.fabSize * 0.415f),
                    )
                }
            }
        }
    }
}

private const val K_DOCK_BODY = 4.5800f      // W_base / H_base (plump 393/412dp native calibration)
private const val K_DOCK_FAB = 1.0000f       // D_fab / H_base
private const val K_DOCK_GAP = 0.1860f       // gap / H_base
private const val K_DOCK_TOTAL = K_DOCK_BODY + K_DOCK_FAB + K_DOCK_GAP // 5.7660f
private const val K_STRIP_FILL = 0.9000f     // W_total / W_screen (~64.2dp @ 411.4dp, ~65.9dp @ 430dp)
private const val K_INSET_H = 0.2150f        // listInsetH / H_base (slotWidth = 1.0375 * H_base for 4 tabs)
private const val K_PILL_H = 0.8680f         // H_pill / H_base (162.14 / 187.2)
private const val K_LENS_H = 1.1940f         // H_lens / H_base (223.5 / 187.2)
private const val K_SLOT_4 = (K_DOCK_BODY - 2f * K_INSET_H) / 4f       // 1.0375f
private const val K_REST_WIDTH_RATIO = (1.6000f * K_PILL_H) / K_SLOT_4 // 1.33860f -> W_pill / H_pill == 1.6000
private const val K_PRESSED_WIDTH_RATIO = (1.4150f * K_LENS_H) / K_SLOT_4 // 1.62844f -> W_lens / H_lens == 1.4150
private const val K_PRESS_WIDTH_GAIN = K_PRESSED_WIDTH_RATIO / K_REST_WIDTH_RATIO

/**
 * Single-scale responsive geometry driven by `H_base(stripWidth)`, locking consistent
 * proportions, optical overhang envelopes, and page clearance across viewport widths.
 *
 * The no-arg [restWidthRatio], [pressedWidthRatio], and [maxVelocityStretch] properties are
 * 4-tab shorthand values; use [restWidthRatio] / [pressedWidthRatio] / [maxVelocityStretch]
 * overloads accepting `tabCount` when querying geometries with 2, 3, or 5 tabs.
 */
@Immutable
public data class DockGeometry(
    public val stripWidth: Dp,
    public val bodyWidth: Dp,
    public val height: Dp = 64.dp,
    public val fabSize: Dp = height,
    public val listInsetH: Dp = height * K_INSET_H,
    public val pillHeight: Dp = height * K_PILL_H,
    public val actionGap: Dp = height * K_DOCK_GAP,
) {
    public val baseExpansionX: Float get() = 0.045f
    public val baseExpansionY: Float get() = 0.045f
    public val pressedPillHeight: Dp get() = height * K_LENS_H

    /** 4-tab shorthand; prefer `restWidthRatio(tabCount)` for variable tab counts. */
    public val restWidthRatio: Float get() = restWidthRatio(4)

    /** 4-tab shorthand; prefer `pressedWidthRatio(tabCount)` for variable tab counts. */
    public val pressedWidthRatio: Float get() = pressedWidthRatio(4)

    public val maxPanelShift: Dp get() = height * 0.055f

    /** 4-tab shorthand; prefer `maxVelocityStretch(tabCount)` for variable tab counts. */
    public val maxVelocityStretch: Float get() = maxVelocityStretch(4)

    public val opticalInset: Dp get() = (height * 0.145f + 9.0.dp).coerceAtLeast(18.dp)
    public val layoutHeight: Dp get() = height + opticalInset * 2
    public val maxPillHeight: Dp get() = pressedPillHeight * 1.04f
    public val pillInsetY: Dp get() = (height - pillHeight) / 2

    public fun slotWidth(tabCount: Int): Dp {
        require(tabCount > 0)
        return (bodyWidth - listInsetH * 2) / tabCount
    }

    public fun restWidthRatio(tabCount: Int): Float {
        require(tabCount > 0)
        if (tabCount == 4) return K_REST_WIDTH_RATIO
        val slot = slotWidth(tabCount)
        val targetWidth = maxOf(slot * 1.06f, pillHeight * 1.6000f)
        val maxContained = slot + listInsetH * 1.70f
        val endExpShift = slot * (((tabCount - 1) / 2f) * baseExpansionX)
        val fabSafeMaxWidth = (slot + listInsetH * 2 + (actionGap - endExpShift - 0.5.dp) * 2) / K_PRESS_WIDTH_GAIN
        return (minOf(targetWidth, maxContained, fabSafeMaxWidth) / slot).coerceAtLeast(1f)
    }

    public fun pressedWidthRatio(tabCount: Int): Float {
        require(tabCount > 0)
        if (tabCount == 4) return K_PRESSED_WIDTH_RATIO
        return restWidthRatio(tabCount) * K_PRESS_WIDTH_GAIN
    }

    public fun maxVelocityStretch(tabCount: Int): Float {
        require(tabCount > 0)
        val sideRoom = (stripWidth - bodyWidth - actionGap - fabSize) / 2
        val endExpansionShift = slotWidth(tabCount) * (((tabCount - 1) / 2f) * baseExpansionX)
        val safeWidth = slotWidth(tabCount) + (sideRoom + listInsetH - maxPanelShift - endExpansionShift - 0.5.dp) * 2
        return (safeWidth / (slotWidth(tabCount) * pressedWidthRatio(tabCount)) - 1f).coerceIn(0f, 0.157f)
    }

    public fun maxPillWidth(tabCount: Int): Dp =
        slotWidth(tabCount) * pressedWidthRatio(tabCount) * (1f + maxVelocityStretch(tabCount))

    public fun horizontalOverhang(tabCount: Int): Dp =
        ((maxPillWidth(tabCount) - slotWidth(tabCount)) / 2 - listInsetH).coerceAtLeast(0.dp)
}

public fun dockGeometry(stripWidth: Dp): DockGeometry {
    val effectiveStrip = stripWidth.coerceIn(308.dp, 422.dp)
    val h = effectiveStrip * (K_STRIP_FILL / K_DOCK_TOTAL)
    return DockGeometry(
        stripWidth = stripWidth,
        bodyWidth = h * K_DOCK_BODY,
        height = h,
        fabSize = h * K_DOCK_FAB,
        listInsetH = h * K_INSET_H,
        pillHeight = h * K_PILL_H,
        actionGap = h * K_DOCK_GAP,
    )
}

public val DockBottomGap: Dp = 12.dp
public fun dockOccupiedHeight(stripWidth: Dp): Dp = dockGeometry(stripWidth).layoutHeight + DockBottomGap
public fun dockClear(stripWidth: Dp): Dp = dockOccupiedHeight(stripWidth) + 30.dp

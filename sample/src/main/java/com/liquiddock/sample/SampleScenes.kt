/*
 * Copyright 2025-2026 Liquid Dock Contributors.
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
package com.liquiddock.sample

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal enum class OpticsMode {
    Grid,
    White,
    Black,
}

/**
 * Scene 0: Dark feed with pauseable ambient sheen + scrollable dark cards.
 * Exercises dark-field rim highlights, resting dark pill contrast, and live moving backdrop.
 */
@Composable
internal fun DarkFeedScene(
    dockClearance: Dp,
    isActive: Boolean,
) {
    var phase by remember { mutableFloatStateOf(0f) }
    val primaryGlowColors = remember {
        listOf(
            Color(0xFF4F46E5).copy(alpha = 0.34f),
            Color(0xFF0EA5E9).copy(alpha = 0.16f),
            Color.Transparent,
        )
    }
    val secondaryGlowColors = remember {
        listOf(
            Color(0xFFF59E0B).copy(alpha = 0.24f),
            Color.Transparent,
        )
    }

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        var lastNanos = Long.MIN_VALUE
        val twoPi = (2.0 * PI).toFloat()
        val omega = twoPi / 9.0f
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != Long.MIN_VALUE) {
                    val dtSec = ((frameNanos - lastNanos).coerceIn(0L, 64_000_000L)) / 1_000_000_000f
                    phase = (phase + omega * dtSec) % twoPi
                }
                lastNanos = frameNanos
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141416)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width * (0.5f + 0.28f * cos(phase))
            val cy = size.height * (0.80f + 0.08f * sin(phase))
            val r1 = size.width * 0.72f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = primaryGlowColors,
                    center = Offset(cx, cy),
                    radius = r1,
                ),
                radius = r1,
                center = Offset(cx, cy),
            )
            val cx2 = size.width * (0.5f - 0.30f * sin(phase * 0.7f))
            val cy2 = size.height * 0.90f
            val r2 = size.width * 0.55f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = secondaryGlowColors,
                    center = Offset(cx2, cy2),
                    radius = r2,
                ),
                radius = r2,
                center = Offset(cx2, cy2),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 16.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = dockClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(Modifier.statusBarsPadding().padding(top = 4.dp, bottom = 6.dp)) {
                    Text(
                        text = stringResource(R.string.scene_dark_title),
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 164.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.scene_dark_subtitle),
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 14.sp,
                    )
                }
            }
            itemsIndexed(DarkCards) { idx, item ->
                val badgeNumber = (idx + 1).toString().padStart(2, '0')
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(item.start, item.end),
                            ),
                        )
                        .padding(18.dp),
                ) {
                    Column(Modifier.align(Alignment.BottomStart)) {
                        Text(
                            text = "$badgeNumber · ${item.tag}",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.subtitle,
                            color = Color.White.copy(alpha = 0.74f),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Scene 1: Crisp pure-white message list with dark avatars, headings, timestamps, and hairline dividers.
 * Exercises high-key light-page glass clarity, outer shadow cleanliness, and white-label legibility.
 */
@Composable
internal fun LightMessageListScene(dockClearance: Dp) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFFFF)),
        contentPadding = PaddingValues(bottom = dockClearance),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Text(
                    text = stringResource(R.string.scene_light_title),
                    color = Color(0xFF111111),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 164.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.scene_light_subtitle),
                    color = Color(0xFF737373),
                    fontSize = 13.sp,
                )
            }
        }
        itemsIndexed(SampleMessages) { index, msg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(msg.avatarBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = msg.initials,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = msg.title,
                            color = Color(0xFF141414),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = msg.time,
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = msg.preview,
                        color = Color(0xFF6B7280),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (index < SampleMessages.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 80.dp, end = 18.dp),
                    thickness = 0.5.dp,
                    color = Color(0xFFEAEAEA),
                )
            }
        }
    }
}

/**
 * Scene 2: Colorful editorial media grid with saturated warm/cool tones.
 * Exercises chromatic transmission through the frosted base and raised droplet lens.
 */
@Composable
internal fun ColorfulMediaScene(dockClearance: Dp) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF18181B)),
        contentPadding = PaddingValues(
            top = 14.dp,
            start = 12.dp,
            end = 12.dp,
            bottom = dockClearance,
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding().padding(start = 6.dp, top = 4.dp, bottom = 6.dp)) {
                Text(
                    text = stringResource(R.string.scene_color_title),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 164.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.scene_color_subtitle),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                )
            }
        }
        items(MediaTiles) { tile ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(196.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(tile.top, tile.bottom)))
                    .padding(14.dp),
            ) {
                Text(
                    text = tile.label,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }
    }
}

/**
 * Scene 3: Precision optical test pattern with toggleable Orthogonal Grid (horizontal + vertical
 * rules and fine bottom ticks over overlapping color discs), Pure White (#FFFFFF) uniform field,
 * and Pure Black (#000000) uniform field.
 */
@Composable
internal fun OpticalRulerScene(
    dockClearance: Dp,
    mode: OpticsMode,
    onModeChange: (OpticsMode) -> Unit,
) {
    val bgColor = when (mode) {
        OpticsMode.Grid -> Color(0xFFF3F1EC)
        OpticsMode.White -> Color(0xFFFFFFFF)
        OpticsMode.Black -> Color(0xFF000000)
    }
    val isLight = mode != OpticsMode.Black
    val titleColor = if (isLight) Color(0xFF1C1917) else Color.White
    val subtitleColor = if (isLight) Color(0xFF44403C) else Color.White.copy(alpha = 0.72f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
    ) {
        if (mode == OpticsMode.Grid) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawCircle(
                    color = Color(0xFFB7C7B0),
                    radius = w * 0.34f,
                    center = Offset(w * 0.28f, h * 0.84f),
                )
                drawCircle(
                    color = Color(0xFFE5BE9E),
                    radius = w * 0.32f,
                    center = Offset(w * 0.68f, h * 0.72f),
                )
                drawCircle(
                    color = Color(0xFFAEC0D6),
                    radius = w * 0.36f,
                    center = Offset(w * 0.84f, h * 0.88f),
                )
                drawCircle(
                    color = Color(0xFF6B3F4E).copy(alpha = 0.45f),
                    radius = w * 0.26f,
                    center = Offset(w * 0.14f, h * 0.66f),
                )

                val gridStartY = maxOf(h * 0.36f, 224.dp.toPx())
                val majorStepPx = 22.dp.toPx()
                val minorStepPx = 11.dp.toPx()
                val majorColor = Color(0xFF23252A).copy(alpha = 0.72f)
                val minorColor = Color(0xFF23252A).copy(alpha = 0.22f)

                var y = gridStartY
                var rowIdx = 0
                while (y < h) {
                    val isMajor = rowIdx % 2 == 0
                    drawLine(
                        color = if (isMajor) majorColor else minorColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = if (isMajor) 1.5.dp.toPx() else 0.75.dp.toPx(),
                    )
                    y += minorStepPx
                    rowIdx++
                }

                var x = majorStepPx
                while (x < w) {
                    drawLine(
                        color = minorColor,
                        start = Offset(x, gridStartY),
                        end = Offset(x, h),
                        strokeWidth = 0.9.dp.toPx(),
                    )
                    x += majorStepPx
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = dockClearance),
        ) {
            Text(
                text = stringResource(R.string.scene_optics_title),
                color = titleColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 164.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.scene_optics_subtitle),
                color = subtitleColor,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SceneToggleChip(
                    label = stringResource(R.string.optics_mode_grid),
                    isLightScene = isLight,
                    active = mode == OpticsMode.Grid,
                    onClick = { onModeChange(OpticsMode.Grid) },
                )
                SceneToggleChip(
                    label = stringResource(R.string.optics_mode_white),
                    isLightScene = isLight,
                    active = mode == OpticsMode.White,
                    onClick = { onModeChange(OpticsMode.White) },
                )
                SceneToggleChip(
                    label = stringResource(R.string.optics_mode_black),
                    isLightScene = isLight,
                    active = mode == OpticsMode.Black,
                    onClick = { onModeChange(OpticsMode.Black) },
                )
            }
        }
    }
}

private data class DarkCard(val tag: String, val title: String, val subtitle: String, val start: Color, val end: Color)
private data class SampleMessage(val initials: String, val title: String, val preview: String, val time: String, val avatarBg: Color)
private data class MediaTile(val label: String, val top: Color, val bottom: Color)

private val DarkCards = listOf(
    DarkCard("OPTICS", "G2 Continuous Capsule SDF", "Seamless curvature transition with dark grazing edges", Color(0xFF1E222B), Color(0xFF101217)),
    DarkCard("REFRACTION", "Two-Layer Backdrop Recording", "Independent base glass and glyph/badge refraction passes", Color(0xFF231D2B), Color(0xFF121016)),
    DarkCard("PHYSICS", "60–240Hz Frame-Clock Springs", "Zero frame starvation and velocity-coupled droplet stretch", Color(0xFF19262A), Color(0xFF0E1417)),
    DarkCard("FALLBACK", "API 26–37 Graceful Support", "Live AGSL shaders on API 33+, blur & neutral fallbacks below", Color(0xFF2A211B), Color(0xFF14110F)),
    DarkCard("SHADOW", "ClipOp.Difference Elevation", "External multi-layer floating shadows with zero interior tint", Color(0xFF1C2321), Color(0xFF0F1312)),
    DarkCard("ACCESSIBILITY", "TalkBack & RTL Ready", "Selectable tab semantics, badge counts, and mirrored layouts", Color(0xFF222431), Color(0xFF111218)),
)

private val SampleMessages = listOf(
    SampleMessage("L", "Liquid Glass Design", "Updated high-key silver base & floating lens comparison", "Just now", Color(0xFF111827)),
    SampleMessage("O", "Optics Lab", "Checking outer rim highlights and lens lift over pure white", "15:15", Color(0xFFD97706)),
    SampleMessage("M", "Motion & Springs", "Recorded 120Hz drag velocity stretch & release curves", "14:42", Color(0xFF2563EB)),
    SampleMessage("K", "Compose Architecture", "Dual-module :liquid-dock + :sample library structure ready", "Yesterday", Color(0xFF059669)),
    SampleMessage("Q", "Device QA Matrix", "Verified API 33+ AGSL shaders alongside API 26–32 fallbacks", "Yesterday", Color(0xFF7C3AED)),
    SampleMessage("T", "Typography Review", "Comparing label stroke weights on dark and white backgrounds", "Yesterday", Color(0xFFDB2777)),
    SampleMessage("S", "Shadow Pass", "Inspecting external halo cleanliness against #FFFFFF lists", "Wed", Color(0xFF0F766E)),
    SampleMessage("R", "Refraction Notes", "End-slot overhang clearance and concentric meniscus edges", "Tue", Color(0xFFB45309)),
    SampleMessage("C", "Compose Community", "Testing drop-in LiquidDockHost with custom content insets", "Mon", Color(0xFF334155)),
    SampleMessage("B", "Release Checklist", "Gradle Wrapper, POM metadata, and JitPack config verified", "Sun", Color(0xFF4F46E5)),
    SampleMessage("A", "Accessibility Audit", "Tab group roles and badge count announcements verified", "Sat", Color(0xFF0284C7)),
    SampleMessage("P", "Performance Trace", "Checking semantics tree invalidation during rapid cross-tab sweep", "Fri", Color(0xFFBE185D)),
    SampleMessage("W", "Window Insets", "Testing 3-button and gesture navigation bars across viewports", "Thu", Color(0xFF15803D)),
    SampleMessage("G", "Geometry Spec", "Single-scale DockGeometry ratios validated for 2–5 tab setups", "Wed", Color(0xFF9333EA)),
    SampleMessage("D", "Dispersion Study", "7-band Cauchy prism sampling constrained inside G2 capsule", "Tue", Color(0xFFEA580C)),
    SampleMessage("V", "Visual Regression", "Comparing static, pressed, dragging, and settled lens states", "Mon", Color(0xFF374151)),
)

private val MediaTiles = listOf(
    MediaTile("Amber Sunset", Color(0xFFF97316), Color(0xFF9A3412)),
    MediaTile("Glacier Cyan", Color(0xFF06B6D4), Color(0xFF1E3A8A)),
    MediaTile("Forest Emerald", Color(0xFF10B981), Color(0xFF064E3B)),
    MediaTile("Dusk Violet", Color(0xFFA855F7), Color(0xFF3B0764)),
    MediaTile("Coral Crimson", Color(0xFFF43F5E), Color(0xFF881337)),
    MediaTile("Solar Gold", Color(0xFFEAB308), Color(0xFF713F12)),
    MediaTile("Abyssal Blue", Color(0xFF3B82F6), Color(0xFF172554)),
    MediaTile("Rosewood", Color(0xFFEC4899), Color(0xFF701A75)),
    MediaTile("Lime Pulse", Color(0xFF84CC16), Color(0xFF365314)),
    MediaTile("Indigo Mist", Color(0xFF6366F1), Color(0xFF312E81)),
    MediaTile("Teal Tide", Color(0xFF14B8A6), Color(0xFF134E4A)),
    MediaTile("Terracotta", Color(0xFFEF4444), Color(0xFF7F1D1D)),
)

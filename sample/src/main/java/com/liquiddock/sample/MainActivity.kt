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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.liquiddock.DockTab
import com.liquiddock.LiquidDockHost
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            MaterialTheme {
                LiquidDockSampleApp()
            }
        }
    }
}

private val BadgeCycleCounts = intArrayOf(13, 120, 1, 0)

@Composable
private fun LiquidDockSampleApp() {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var programmaticScrollJob by remember { mutableStateOf<Job?>(null) }

    var badgeMode by rememberSaveable { mutableIntStateOf(0) }
    var showActionButton by rememberSaveable { mutableStateOf(true) }
    var opticsMode by rememberSaveable { mutableStateOf(OpticsMode.Grid) }
    var actionTapCount by rememberSaveable { mutableIntStateOf(0) }
    var actionTriggerId by remember { mutableIntStateOf(0) }
    var showActionToast by remember { mutableStateOf(false) }

    val badgeCount = BadgeCycleCounts[badgeMode % BadgeCycleCounts.size]
    val isLightScene = selectedTab == 1 || (selectedTab == 3 && opticsMode != OpticsMode.Black)
    val darkSceneActive = selectedTab == 0 ||
        (pagerState.isScrollInProgress && (pagerState.currentPage == 0 || pagerState.targetPage == 0))

    // Sync user swipe settlements back into `selectedTab` without midpoint jitter or drag echo.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress to pagerState.settledPage }
            .collect { (scrolling, settled) ->
                if (!scrolling && programmaticScrollJob?.isActive != true) {
                    selectedTab = settled
                }
            }
    }

    val view = LocalView.current
    LaunchedEffect(view, isLightScene) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = isLightScene
        controller.isAppearanceLightNavigationBars = isLightScene
    }

    LaunchedEffect(actionTriggerId, showActionButton) {
        if (!showActionButton || actionTriggerId == 0) {
            showActionToast = false
            return@LaunchedEffect
        }
        showActionToast = true
        delay(1400)
        showActionToast = false
    }

    val homeLabel = stringResource(R.string.tab_home)
    val inboxLabel = stringResource(R.string.tab_inbox)
    val discoverLabel = stringResource(R.string.tab_discover)
    val opticsLabel = stringResource(R.string.tab_optics)
    val tabs = remember(homeLabel, inboxLabel, discoverLabel, opticsLabel, badgeCount) {
        listOf(
            DockTab(label = homeLabel),
            DockTab(label = inboxLabel, badgeCount = badgeCount),
            DockTab(label = discoverLabel),
            DockTab(label = opticsLabel),
        )
    }

    val badgeValueText = when {
        badgeCount <= 0 -> stringResource(R.string.toggle_badge_off)
        badgeCount > 99 -> "99+"
        else -> badgeCount.toString()
    }

    Box(Modifier.fillMaxSize()) {
        LiquidDockHost(
            tabs = tabs,
            selectedIndex = selectedTab,
            onSelect = { target ->
                selectedTab = target
                val previous = programmaticScrollJob
                val job = scope.launch {
                    pagerState.animateScrollToPage(target)
                }
                programmaticScrollJob = job
                previous?.cancel()
                job.invokeOnCompletion { cause ->
                    if (programmaticScrollJob === job) {
                        programmaticScrollJob = null
                        if (cause == null) {
                            selectedTab = pagerState.settledPage
                        }
                    }
                }
            },
            actionIcon = if (showActionButton) Icons.Filled.Add else null,
            actionContentDescription = if (showActionButton) stringResource(R.string.action_create_desc) else null,
            onAction = if (showActionButton) {
                {
                    actionTapCount++
                    actionTriggerId++
                }
            } else {
                null
            },
        ) { dockClearance ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                when (page) {
                    0 -> DarkFeedScene(dockClearance = dockClearance, isActive = darkSceneActive)
                    1 -> LightMessageListScene(dockClearance = dockClearance)
                    2 -> ColorfulMediaScene(dockClearance = dockClearance)
                    else -> OpticalRulerScene(
                        dockClearance = dockClearance,
                        mode = opticsMode,
                        onModeChange = { opticsMode = it },
                    )
                }
            }
        }

        // Minimal top-right toggles for quick visual benchmarking on device.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(top = 8.dp, end = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SceneToggleChip(
                label = stringResource(R.string.toggle_badge_format, badgeValueText),
                isLightScene = isLightScene,
                active = badgeCount > 0,
                onClick = { badgeMode = (badgeMode + 1) % BadgeCycleCounts.size },
            )
            SceneToggleChip(
                label = stringResource(
                    if (showActionButton) R.string.toggle_action_on else R.string.toggle_action_off,
                ),
                isLightScene = isLightScene,
                active = showActionButton,
                onClick = { showActionButton = !showActionButton },
            )
        }

        AnimatedVisibility(
            visible = showActionToast,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 56.dp),
        ) {
            Text(
                text = stringResource(R.string.action_toast_format, actionTapCount),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
internal fun SceneToggleChip(
    label: String,
    isLightScene: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    val bg = when {
        isLightScene && active -> Color(0xFF1C1917).copy(alpha = 0.88f)
        isLightScene -> Color.White.copy(alpha = 0.90f)
        active -> Color.White.copy(alpha = 0.24f)
        else -> Color(0xFF1F2024).copy(alpha = 0.78f)
    }
    val fg = when {
        isLightScene && !active -> Color(0xFF1C1917)
        else -> Color.White
    }
    val borderColor = when {
        isLightScene && !active -> Color(0xFF1C1917).copy(alpha = 0.18f)
        isLightScene -> Color.Transparent
        active -> Color.White.copy(alpha = 0.24f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bg,
        contentColor = fg,
        border = if (borderColor.alpha > 0f) BorderStroke(1.dp, borderColor) else null,
        modifier = Modifier.defaultMinSize(minHeight = 36.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

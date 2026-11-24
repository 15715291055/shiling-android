package com.fuguang.localdiary.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds

@Composable
internal fun MainTabTransition(tab: MainTab, content: @Composable (MainTab) -> Unit) {
    AnimatedContent(
        targetState = tab,
        modifier = Modifier.fillMaxSize().clipToBounds(),
        transitionSpec = {
            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
            val incoming = CubicBezierEasing(.4f, 0f, .2f, 1f)
            val outgoing = CubicBezierEasing(.4f, 0f, .2f, 1f)
            // Short, opposing travel gives depth without moving the wallpaper or navigation.
            (slideInHorizontally(tween(560, easing = incoming)) { direction * it / 9 } +
                fadeIn(tween(400, delayMillis = 50))) togetherWith
                (slideOutHorizontally(tween(420, easing = outgoing)) { -direction * it / 24 } +
                    fadeOut(tween(300)))
        },
        label = "directional-tab-transition",
        content = { content(it) }
    )
}

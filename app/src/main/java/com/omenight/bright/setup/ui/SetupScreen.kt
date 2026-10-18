package com.omenight.bright.setup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Composable
fun SetupScreen(onFinish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    // Setup is Home until it completes, Back on the first step has nowhere to go
    BackHandler { if (step > 0) step-- }

    AnimatedContent(
        targetState = step,
        transitionSpec = { fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90)) },
        label = "step",
    ) { current ->
        when (current) {
            0 -> RemotePage(onNext = { step = 1 })
            1 -> LanguagePage(onBack = { step = 0 }, onNext = { step = 2 })
            else -> WifiPage(onBack = { step = 1 }, onFinish = onFinish)
        }
    }
}

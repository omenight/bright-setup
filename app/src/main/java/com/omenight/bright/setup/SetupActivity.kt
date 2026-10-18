package com.omenight.bright.setup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.omenight.bright.setup.data.SystemSetup
import com.omenight.bright.setup.ui.BackdropBaker
import com.omenight.bright.setup.ui.LocalBackdrop
import com.omenight.bright.setup.ui.SetupScreen
import com.omenight.bright.setup.ui.backdrop
import com.omenight.bright.setup.ui.theme.BrightColors
import com.omenight.bright.setup.ui.theme.BrightTheme

class SetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemSetup.applyTimeDefaults(this)
        // A 320x180 bake takes a few milliseconds, not worth a placeholder frame
        val backdrop = BackdropBaker.bake()
        setContent {
            BrightTheme {
                CompositionLocalProvider(LocalBackdrop provides backdrop) {
                    Box(Modifier.fillMaxSize().backdrop(backdrop, BrightColors.Background)) {
                        SetupScreen(onFinish = { SystemSetup.complete(this@SetupActivity) })
                    }
                }
            }
        }
    }
}

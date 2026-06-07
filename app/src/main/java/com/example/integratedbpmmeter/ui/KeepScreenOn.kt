package com.example.integratedbpmmeter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
fun KeepScreenOn(enabled: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        if (!enabled) {
            onDispose { }
        } else {
            val previous = view.keepScreenOn
            view.keepScreenOn = true
            onDispose {
                view.keepScreenOn = previous
            }
        }
    }
}

package com.glmwebshell.core.ui.insets

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable

/**
 * Per TZ §6.1.1 — correct WindowInsets handling for a fullscreen WebView.
 * Edge-to-edge layout; the WebView itself receives the inner padding via the
 * bridge-boot.js so its scrollbar is not clipped under the status bar.
 */
object GlmInsets {
    @Composable
    fun systemBars() = WindowInsets.systemBars
}

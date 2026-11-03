package io.github.henry753951.airplayremote.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Glass panel: translucent fill, a bright rim and a soft top highlight.
 * No blur yet: blur needs the frame time test from T006 on the TV.
 * When [enabled] is false (user switch, or frames over 33 ms) it is a plain panel.
 */
fun Modifier.glassPanel(shape: Shape, enabled: Boolean): Modifier =
    if (enabled) {
        this.background(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.07f))),
            shape,
        ).border(
            1.dp,
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.08f))),
            shape,
        )
    } else {
        this.background(Color(0xE61C1C1F), shape)
    }

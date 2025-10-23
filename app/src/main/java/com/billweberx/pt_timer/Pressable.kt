package com.billweberx.pt_timer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.collectLatest

/**
 * A custom modifier that adds a press-and-release scaling animation.
 *
 * It now includes an `enabled` parameter. When `enabled` is false, it will not
 * respond to clicks OR show the press animation.
 */
fun Modifier.pressable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true, // <-- NEW PARAMETER
    onClick: () -> Unit
) = composed {
    var isPressed by remember { mutableStateOf(false) }
    // The scale will only change if the button is enabled AND pressed.
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) 0.90f else 1f, // <-- NOW CHECKS ENABLED
        label = "PressScale"
    )

    // We only listen for interactions if the modifier is enabled.
    LaunchedEffect(interactionSource, enabled) { // <-- NOW DEPENDS ON ENABLED
        if (enabled) {
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> isPressed = true
                    is PressInteraction.Release -> isPressed = false
                    is PressInteraction.Cancel -> isPressed = false
                }
            }
        } else {
            // If disabled, ensure we are not in a pressed state.
            isPressed = false
        }
    }

    this
        .graphicsLayer {
            // Apply the scaling effect
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null, // Disable ripple
            enabled = enabled, // <-- PASS ENABLED TO CLICKABLE
            onClick = onClick
        )
}

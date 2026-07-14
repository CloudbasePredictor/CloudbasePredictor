package com.cloudbasepredictor.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Keyboard affordance shared by the hand-built web overlays (favorites dialog, save-favorite dialog,
 * forecast-model sheet). These are drawn as in-canvas Box + scrim + Surface rather than Compose
 * `Dialog`/`Popup` (unreliable on wasmJs), so they do not get the platform dialog's Escape handling
 * or focus move for free.
 *
 * Apply this immediately before the scrim's `clickable`: the requester binds to that focus target, so
 * opening the overlay moves focus into it (off the content behind the scrim) and Escape closes it via
 * a preview key handler that sees the event before any focused child consumes it.
 *
 * Scope note: this implements Escape + initial focus. A full focus trap (Tab containment) and
 * restoring focus to the exact trigger control on close are intentionally omitted — they are
 * disproportionate to build reliably on the single-canvas wasmJs Compose target. That interaction is
 * verified by the release gates, not unit tests, in this setup.
 */
@Composable
internal fun Modifier.dismissibleOverlay(onDismiss: () -> Unit): Modifier {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) {
        runCatching { focusRequester.requestFocus() }
    }
    return this
        .focusRequester(focusRequester)
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onDismiss()
                true
            } else {
                false
            }
        }
}

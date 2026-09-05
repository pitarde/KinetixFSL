package com.example.kinetixfsl.community

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * Presents [content] as a tall Material3 bottom sheet: dimmed backdrop still
 * shows a sliver of the screen behind it, rounded top corners, a drag handle,
 * slides up on entry — like a phone OS's "compose" sheet, not a screen that
 * swallows the whole display.
 *
 * A hand-rolled version of this (an Animatable offset plus a raw
 * detectVerticalDragGestures, first on a small handle zone, then widened with
 * a NestedScrollConnection for the scrollable body) only ever reliably caught
 * drags over the handle and the *non-text-field* parts of the scrollable
 * body — the fixed top bar, the fixed bottom toolbar, and a focused text
 * field's own drag handling all won the gesture instead of letting it
 * through. Material3's ModalBottomSheet already solves exactly this: its
 * drag-to-dismiss wiring is nested-scroll-aware from the container down, so a
 * swipe is recognized correctly no matter what's underneath it.
 *
 * Swiping down past the dismiss point, tapping the scrim, or back-press all
 * trigger [onDismissRequest] automatically — routed here through [dismiss] so
 * the sheet finishes hiding itself before [onClose] actually removes it from
 * the host's overlay list; [content] gets that same `dismiss` for its own
 * close button. Used for Create/Edit post specifically — see their call
 * sites in CommunityScreen.kt and CommunityHomeScreen.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SlideUpScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    // Always fully expanded rather than pausing at a half-open height first —
    // right for a full editor, not a short pick-one list.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var dismissing by remember { mutableStateOf(false) }

    fun dismiss() {
        if (dismissing) return
        dismissing = true
        scope.launch {
            sheetState.hide()
            onClose()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { dismiss() },
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        // Caps how tall the sheet grows to — content wanting fillMaxSize()
        // would otherwise expand it almost to the very top of the screen,
        // leaving barely any of the backdrop showing above it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
        ) {
            content { dismiss() }
        }
    }
}

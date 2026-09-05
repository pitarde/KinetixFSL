package com.example.kinetixfsl.community

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The sheet behind a post's 3-dot button: copy the text, delete the post, or
 * open it in the editor.
 *
 * Dimmed backdrop with a close button top-right, matching the mockup. Tapping
 * the backdrop or pressing back also closes it.
 */
@Composable
internal fun PostActionsSheet(
    onCopyText: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionsSheet(onDismiss = onDismiss, modifier = modifier) {
        ActionRow(CommunityIcons.CopyText, "Copy text", onCopyText)
        ActionRow(
            icon = CommunityIcons.Delete,
            label = "Delete",
            onClick = onDelete,
            tint = MaterialTheme.colorScheme.error,
        )
        ActionRow(CommunityIcons.EditPost, "Edit post", onEdit)
    }
}

/**
 * The same sheet for a comment. No "Copy text" — a comment is a line of text
 * the user already wrote, so copying it isn't a useful action here.
 */
/**
 * The post menu on somebody else's profile. Nothing destructive — you can copy
 * the text or follow the author, and that's it.
 */
@Composable
internal fun VisitorPostActionsSheet(
    onCopyText: () -> Unit,
    onFollow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionsSheet(onDismiss = onDismiss, modifier = modifier) {
        ActionRow(CommunityIcons.CopyText, "Copy text", onCopyText)
        ActionRow(CommunityIcons.Follow, "Follow post", onFollow)
    }
}

/**
 * The post menu on someone else's post in a feed — the Home Feed and a
 * community's own feed both use it. Copy and Report are always there; Share
 * and Hide are Home Feed-only (a community feed already has nowhere else for
 * a post to hide to), so they're only shown when a handler is passed.
 */
@Composable
internal fun OtherPostActionsSheet(
    onCopyText: () -> Unit,
    onReport: () -> Unit,
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    /** True when the post is already hidden — swaps the label to "Unhide". */
    isHidden: Boolean = false,
    modifier: Modifier = Modifier,
) {
    ActionsSheet(onDismiss = onDismiss, modifier = modifier) {
        ActionRow(CommunityIcons.CopyText, "Copy text", onCopyText)
        ActionRow(CommunityIcons.Report, "Report", onReport)
        if (onShare != null) {
            ActionRow(CommunityIcons.Share, "Share", onShare)
        }
        if (onHide != null) {
            ActionRow(CommunityIcons.Hide, if (isHidden) "Unhide" else "Hide", onHide)
        }
    }
}

@Composable
internal fun CommentActionsSheet(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionsSheet(onDismiss = onDismiss, modifier = modifier) {
        ActionRow(CommunityIcons.EditPost, "Edit comment", onEdit)
        ActionRow(
            icon = CommunityIcons.Delete,
            label = "Delete comment",
            onClick = onDelete,
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * The shared shell: dimmed backdrop, rounded sheet, close button.
 *
 * Slides up from off-screen the moment it's composed, and slides back down
 * before actually calling [onDismiss] — from tapping the backdrop, pressing
 * back, or swiping the sheet itself down past a threshold — Reddit-style,
 * rather than the "menu just vanishes" a plain `if (target != null)` gives
 * you. A single [Animatable] drives the sheet's Y offset for all three exits
 * (and for the drag itself) so they never fight a separate exit transition.
 */
@Composable
private fun ActionsSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    // Comfortably taller than any sheet this shell will ever hold, so
    // animating to this offset always lands fully off-screen regardless of
    // how many action rows are inside.
    val offscreenPx = with(density) { 1200.dp.toPx() }
    val dismissThresholdPx = with(density) { 120.dp.toPx() }
    val offsetY = remember { Animatable(offscreenPx) }
    val scope = rememberCoroutineScope()
    var dismissing by remember { mutableStateOf(false) }

    fun dismiss() {
        if (dismissing) return
        dismissing = true
        scope.launch {
            offsetY.animateTo(offscreenPx, animationSpec = tween(220, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        offsetY.animateTo(0f, animationSpec = tween(280, easing = FastOutSlowInEasing))
    }

    BackHandler(onBack = { dismiss() })

    // Fades in lockstep with the sheet's own slide, instead of the backdrop
    // just snapping to fully dimmed the instant the sheet appears.
    val backdropAlpha = 0.45f * (1f - (offsetY.value / offscreenPx)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backdropAlpha))
            .clickable(onClick = { dismiss() }),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                // Swipe down anywhere on the sheet to dismiss it; dragging up
                // is clamped at the resting position rather than overshooting.
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetY.snapTo((offsetY.value + dragAmount).coerceAtLeast(0f))
                            }
                        },
                        onDragEnd = {
                            if (offsetY.value > dismissThresholdPx) {
                                dismiss()
                            } else {
                                scope.launch {
                                    offsetY.animateTo(0f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                                }
                            }
                        },
                    )
                }
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(MaterialTheme.colorScheme.surface)
                // Swallow taps so they don't fall through to the backdrop.
                .clickable(enabled = false) {}
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
        ) {
            // The drag handle — the visual cue that this can be swiped down.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 16.dp, top = 4.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = { dismiss() }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = CommunityIcons.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            actions()

            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * Deleting a post can't be undone, so it gets a confirmation step rather than
 * firing straight off the menu.
 */
@Composable
internal fun ConfirmDeleteDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    heading: String = "Delete post?",
    subject: String = "post",
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = if (title.isBlank()) {
                    "This $subject will be removed for everyone. This can't be undone."
                } else {
                    "\"$title\" will be removed for everyone. This can't be undone."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Text(
                text = "Delete",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onConfirm)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
        dismissButton = {
            Text(
                text = "Cancel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Editing a comment: its text, plus — when it carries a photo — a way to
 * remove that photo (the X) or swap it for another (Change photo).
 */
@Composable
internal fun EditCommentDialog(
    initialText: String,
    initialImageUrl: String?,
    /** [newImageUri] is a freshly picked local file when the photo was swapped;
     *  [removeImage] is true when the X was tapped. Both null/false leaves the
     *  existing photo untouched. */
    onConfirm: (text: String, newImageUri: Uri?, removeImage: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(initialText) { mutableStateOf(initialText) }

    // Local photo state: a freshly picked replacement, or a flag that the
    // original was removed. Neither set means "keep whatever it already had".
    var pickedUri by remember(initialImageUrl) { mutableStateOf<Uri?>(null) }
    var imageRemoved by remember(initialImageUrl) { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            pickedUri = uri
            imageRemoved = false
        }
    }

    val displayedImage: Any? = pickedUri ?: initialImageUrl.takeUnless { imageRemoved || it.isNullOrBlank() }
    val imageChanged = pickedUri != null || imageRemoved
    val canSave = (text.isNotBlank() || displayedImage != null) && (text != initialText || imageChanged)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit comment",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(
                            MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (displayedImage != null) {
                    Spacer(Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = displayedImage,
                            contentDescription = "Attached image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(12.dp),
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable {
                                    pickedUri = null
                                    imageRemoved = true
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = CommunityIcons.Close,
                                contentDescription = "Remove photo",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Change photo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                imagePicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            }
                            .padding(vertical = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            Text(
                text = "Save",
                style = MaterialTheme.typography.labelLarge,
                color = if (canSave) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(enabled = canSave) { onConfirm(text, pickedUri, imageRemoved) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
        dismissButton = {
            Text(
                text = "Cancel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Asks the reporter why they're reporting something before it's filed, so the
 * moderator in the admin console has the reporter's own words to act on. A few
 * quick presets plus a free-text box; Submit is enabled once there's a reason.
 */
@Composable
internal fun ReportReasonDialog(
    onSubmit: (reason: String) -> Unit,
    onDismiss: () -> Unit,
    subject: String = "post",
) {
    val presets = listOf(
        "Spam or misleading",
        "Harassment or bullying",
        "Inappropriate content",
        "Incorrect sign / misinformation",
        "Other",
    )
    var selected by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }

    // The final reason is the preset, plus any typed detail. "Other" requires
    // the text box; every other preset can stand on its own.
    val reason = buildString {
        selected?.let { if (it != "Other") append(it) }
        if (details.isNotBlank()) {
            if (isNotEmpty()) append(" — ")
            append(details.trim())
        }
    }
    val canSubmit = reason.isNotBlank()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Report $subject",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = "Tell us what's wrong. Our moderators review every report.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                presets.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selected = preset }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected == preset) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected == preset) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onPrimary),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = preset,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (details.isEmpty()) {
                        Text(
                            text = if (selected == "Other") "Describe the problem" else "Add details (optional)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = details,
                        onValueChange = { details = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(
                            MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Text(
                text = "Submit",
                style = MaterialTheme.typography.labelLarge,
                color = if (canSubmit) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(enabled = canSubmit) { onSubmit(reason) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
        dismissButton = {
            Text(
                text = "Cancel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            fontWeight = FontWeight.Medium,
        )
    }
}

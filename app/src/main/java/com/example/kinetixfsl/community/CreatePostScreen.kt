package com.example.kinetixfsl.community

import androidx.activity.compose.BackHandler
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.kinetixfsl.community.model.MAX_POST_MEDIA
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme
import com.example.kinetixfsl.ui.theme.KinetixInk
import com.example.kinetixfsl.ui.theme.KinetixWhite



/**
 * The create-post screen. Matches the design: X (close), Select Community pill,
 * title/body fields, bottom toolbar with link/image/video, and a Post button.
 *
 * Flow: user fills title → optionally adds body → optionally picks an image
 * or video from the gallery → taps Post → media uploads to Cloudinary →
 * Firestore document is written → feed updates live → this screen closes.
 */
@Composable
fun CreatePostScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreatePostViewModel = viewModel(),
    /**
     * When set, the composer is locked to this community (opened from inside it)
     * — the community pill shows its name and can't be changed.
     */
    lockedCommunityId: String? = null,
    lockedCommunityName: String? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val joinedCommunities by viewModel.joinedCommunities.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Lock the target once when opened from within a community.
    LaunchedEffect(lockedCommunityId) {
        if (lockedCommunityId != null) {
            viewModel.presetLockedCommunity(lockedCommunityId, lockedCommunityName.orEmpty())
        }
    }

    var showCommunityPicker by remember { mutableStateOf(false) }

    // Request notification permission on Android 13+ so the upload
    // progress notification actually appears in the status bar.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // Whether granted or denied, proceed with the post.
        // If denied, the upload still works — they just won't see the notification.
        viewModel.submitPost(context)
    }

    // Phone back button closes create-post and returns to the feed.
    BackHandler(onBack = onClose)

    if (state.isPostCreated) {
        viewModel.onPostCreatedHandled()
        onClose()
    }

    // Photo picker launchers — the modern Android way (no permissions needed).
    // Multi-select, so a post can carry images and videos together; picking
    // again appends rather than replacing.
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_POST_MEDIA),
    ) { uris: List<Uri> ->
        viewModel.onMediaPicked(uris.map { PickedMedia(it, "image") })
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_POST_MEDIA),
    ) { uris: List<Uri> ->
        viewModel.onMediaPicked(uris.map { PickedMedia(it, "video") })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ---- Top bar: X + Select Community + Post ----
        // ---- Top bar: X + Select Community + Post ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Close (X) button
            Icon(
                imageVector = CloseIcon,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onClose),
            )

            Spacer(Modifier.width(12.dp))

            // Community selector pill. Shows the current target (a community or
            // "Home Feed"); tapping opens the picker unless the target is locked.
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(enabled = !state.isCommunityLocked) { showCommunityPicker = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.communityLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!state.isCommunityLocked) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = ChevronDownIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Post button
            val canPost = state.title.isNotBlank()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (canPost) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    .clickable(enabled = canPost) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            // On Android 13+, check notification permission first.
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.submitPost(context)
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Post",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        // ---- Error ----
        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage!!,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        // ---- Content area (scrollable) ----
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            // Title field
            PlainTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                placeholder = "Title",
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )

            Spacer(Modifier.height(8.dp))

            // Body field
            PlainTextField(
                value = state.body,
                onValueChange = viewModel::onBodyChange,
                placeholder = "Body text (optional)",
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            )


            // One field per link the user added — the toolbar link button adds
            // another. Each has an "x" to drop it.
            state.links.forEachIndexed { index, link ->
                Spacer(Modifier.height(12.dp))
                LinkFieldRow(
                    value = link,
                    onValueChange = { viewModel.onLinkChange(index, it) },
                    onRemove = { viewModel.removeLink(index) },
                )
            }

            Spacer(Modifier.height(16.dp))

            // Everything picked so far, as a scrollable thumbnail strip.
            if (state.media.isNotEmpty()) {
                Text(
                    text = "${state.media.size} of $MAX_POST_MEDIA attached",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.media, key = { it.uri.toString() }) { item ->
                        Box(modifier = Modifier.size(120.dp)) {
                            // AsyncImage renders a video's first frame as its
                            // thumbnail, so one composable covers both types.
                            AsyncImage(
                                model = item.uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(12.dp),
                                    ),
                            )

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(KinetixInk.copy(alpha = 0.6f))
                                    .clickable { viewModel.removeMedia(item) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = CloseIcon,
                                    contentDescription = "Remove",
                                    tint = KinetixWhite,
                                    modifier = Modifier.size(13.dp),
                                )
                            }

                            if (item.type == "video") {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(KinetixInk.copy(alpha = 0.6f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "Video",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = KinetixWhite,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // ---- Bottom toolbar: link, image, video ----
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarIcon(
                icon = CommunityIcons.Link,
                description = "Add link",
                onClick = { viewModel.addLinkField() },
            )
            Spacer(Modifier.width(20.dp))
            ToolbarIcon(
                icon = ImageIcon,
                description = "Add images",
                enabled = state.canAddMore,
                onClick = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )
            Spacer(Modifier.width(20.dp))
            ToolbarIcon(
                icon = VideoIcon,
                description = "Add videos",
                enabled = state.canAddMore,
                onClick = {
                    videoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
            )
        }
    }

    if (showCommunityPicker) {
        CommunityPickerSheet(
            communities = joinedCommunities,
            selectedId = state.selectedCommunityId,
            onPick = { id, name ->
                viewModel.selectCommunity(id, name)
                showCommunityPicker = false
            },
            onDismiss = { showCommunityPicker = false },
        )
    }
}

/**
 * Bottom sheet for choosing where a post goes: the Home Feed, or any community
 * the user has joined. Only members can post to a community, so the list is
 * exactly their joined set.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CommunityPickerSheet(
    communities: List<com.example.kinetixfsl.community.model.Community>,
    selectedId: String,
    onPick: (id: String, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = "Post to",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            CommunityPickerRow(
                label = "Home Feed",
                selected = selectedId.isBlank(),
                onClick = { onPick("", "") },
            )
            communities.forEach { community ->
                CommunityPickerRow(
                    label = community.name,
                    selected = community.id == selectedId,
                    onClick = { onPick(community.id, community.name) },
                )
            }
            if (communities.isEmpty()) {
                Text(
                    text = "Join a community to post to it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CommunityPickerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * One editable link, in a bordered pill with an "x" to remove it. Shared by the
 * create and edit composers so a link field looks the same in both.
 */
@Composable
internal fun LinkFieldRow(
    value: String,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CommunityIcons.Link,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            "Paste your link here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CommunityIcons.Close,
                contentDescription = "Remove link",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** A bare text field with no border — just the text and a placeholder. */
@Composable
private fun PlainTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun ToolbarIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.outline
        },
        modifier = Modifier
            .size(28.dp)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

// ---- Small inline icons (only used on this screen, not worth externalizing) ----

private val CloseIcon: ImageVector by lazy {
    ImageVector.Builder("Close", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f) {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }.build()
}

private val ChevronDownIcon: ImageVector by lazy {
    ImageVector.Builder("ChevronDown", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.White), strokeLineWidth = 2.2f) {
            moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
        }
    }.build()
}

private val ImageIcon: ImageVector by lazy {
    ImageVector.Builder("Image", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
            // Frame
            moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(4f, 19f); close()
            // Mountain
            moveTo(4f, 17f); lineTo(9f, 12f); lineTo(12f, 15f); lineTo(16f, 10f); lineTo(20f, 17f)
        }
        path(fill = SolidColor(Color.Black)) {
            // Sun dot
            moveTo(10f, 9f)
            arcTo(1.5f, 1.5f, 0f, true, true, 7f, 9f)
            arcTo(1.5f, 1.5f, 0f, true, true, 10f, 9f)
            close()
        }
    }.build()
}

private val VideoIcon: ImageVector by lazy {
    ImageVector.Builder("Video", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
            // Camera body
            moveTo(3f, 6f); lineTo(16f, 6f); lineTo(16f, 18f); lineTo(3f, 18f); close()
            // Lens triangle
            moveTo(16f, 9f); lineTo(21f, 6f); lineTo(21f, 18f); lineTo(16f, 15f)
        }
    }.build()
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CreatePostScreenPreview() {
    KinetixFSLTheme {
        CreatePostScreen(
            onClose = {},
        )
    }
}

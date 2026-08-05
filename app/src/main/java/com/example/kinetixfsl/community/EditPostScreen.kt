package com.example.kinetixfsl.community

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.kinetixfsl.community.model.MAX_POST_MEDIA
import com.example.kinetixfsl.community.model.Post
import com.example.kinetixfsl.ui.theme.KinetixInk
import com.example.kinetixfsl.ui.theme.KinetixWhite

/**
 * Edits an existing post. Same layout as [CreatePostScreen] so it feels like
 * the same tool, but a distinct screen with its own state: the action button
 * says **Save post**, and the attachment strip mixes media already on the post
 * with newly picked files.
 */
@Composable
fun EditPostScreen(
    post: Post,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(post.id) { EditPostViewModel(post) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val joinedCommunities by viewModel.joinedCommunities.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCommunityPicker by remember { mutableStateOf(false) }

    // On Android 13+ the upload notification needs permission; ask once, then
    // save either way (a denied prompt only costs the progress notification).
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ -> viewModel.save(context) }

    fun saveWithNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.save(context)
        }
    }

    BackHandler(onBack = onClose)

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onClose()
    }

    // Separate image and video pickers, mirroring the create screen — so a user
    // who forgot to add a video can add one here just as they would on create.
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
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            // See CreatePostScreen — edge-to-edge means nothing resizes for the
            // keyboard without this, so a long body scrolls behind it.
            .imePadding(),
    ) {
        // ---- Screen title: the one thing that differs from the create screen ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Edit post",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        }

        // ---- Top bar: X + community pill + Post (identical to create) ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = CommunityIcons.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onClose),
            )

            Spacer(Modifier.width(12.dp))

            // Community selector pill.
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { showCommunityPicker = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.communityLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = CommunityIcons.ChevronDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (state.canSave) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                    .clickable(enabled = state.canSave) { saveWithNotificationPermission() }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        text = "Post",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (state.isSaving && state.savingLabel.isNotBlank()) {
            Text(
                text = state.savingLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage!!,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        // ---- Fields ----
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            EditField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                placeholder = "Title",
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )

            Spacer(Modifier.height(8.dp))

            EditField(
                value = state.body,
                onValueChange = viewModel::onBodyChange,
                placeholder = "Body text (optional)",
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )

            // One field per link — the toolbar link button adds another.
            state.links.forEachIndexed { index, link ->
                Spacer(Modifier.height(12.dp))
                LinkFieldRow(
                    value = link,
                    onValueChange = { viewModel.onLinkChange(index, it) },
                    onRemove = { viewModel.removeLink(index) },
                )
            }

            Spacer(Modifier.height(16.dp))

            if (state.totalMedia > 0) {
                Text(
                    text = "${state.totalMedia} of $MAX_POST_MEDIA attached",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Already on the post — the thumbnail is a remote URL.
                    items(
                        count = state.existingMedia.size,
                        key = { index -> "existing-${state.existingMedia[index].url}" },
                    ) { index ->
                        val item = state.existingMedia[index]
                        MediaThumb(
                            model = mediaUrl(item.feedUrl),
                            isVideo = item.isVideo,
                            onRemove = { viewModel.removeExisting(item) },
                        )
                    }
                    // Newly picked — a local content URI.
                    items(
                        count = state.newMedia.size,
                        key = { index -> "new-${state.newMedia[index].uri}" },
                    ) { index ->
                        val item = state.newMedia[index]
                        MediaThumb(
                            model = item.uri,
                            isVideo = item.type == "video",
                            onRemove = { viewModel.removeNew(item) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // ---- Bottom toolbar: link, image, video (identical to create) ----
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = CommunityIcons.Link,
                contentDescription = "Add link",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(26.dp)
                    .clickable { viewModel.addLinkField() },
            )
            Spacer(Modifier.width(20.dp))
            Icon(
                imageVector = CommunityIcons.Image,
                contentDescription = "Add images",
                tint = if (state.canAddMore) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier
                    .size(26.dp)
                    .clickable(enabled = state.canAddMore) {
                        imagePicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
            )
            Spacer(Modifier.width(20.dp))
            Icon(
                imageVector = VideoToolbarIcon,
                contentDescription = "Add videos",
                tint = if (state.canAddMore) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier
                    .size(26.dp)
                    .clickable(enabled = state.canAddMore) {
                        videoPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.VideoOnly
                            )
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

/** Video-camera glyph for the toolbar — matches the create screen's. */
private val VideoToolbarIcon: ImageVector by lazy {
    ImageVector.Builder("Video", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
            moveTo(3f, 6f); lineTo(16f, 6f); lineTo(16f, 18f); lineTo(3f, 18f); close()
            moveTo(16f, 9f); lineTo(21f, 6f); lineTo(21f, 18f); lineTo(16f, 15f)
        }
    }.build()
}

@Composable
private fun MediaThumb(
    model: Any?,
    isVideo: Boolean,
    onRemove: () -> Unit,
) {
    Box(modifier = Modifier.size(120.dp)) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(KinetixInk.copy(alpha = 0.6f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CommunityIcons.Close,
                contentDescription = "Remove",
                tint = KinetixWhite,
                modifier = Modifier.size(13.dp),
            )
        }
        if (isVideo) {
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

@Composable
private fun EditField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    singleLine: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        singleLine = singleLine,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                inner()
            }
        },
    )
}

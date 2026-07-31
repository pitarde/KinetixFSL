package com.example.kinetixfsl.community

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
    val context = LocalContext.current

    BackHandler(onBack = onClose)

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onClose()
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_POST_MEDIA),
    ) { uris: List<Uri> ->
        viewModel.onMediaPicked(
            uris.map { uri ->
                val mime = context.contentResolver.getType(uri).orEmpty()
                PickedMedia(uri, if (mime.startsWith("video")) "video" else "image")
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        // ---- Top bar: X | Edit post | Save post ----
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
                    .size(28.dp)
                    .clickable(onClick = onClose),
            )

            Spacer(Modifier.width(14.dp))

            Text(
                text = "Edit post",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )

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
                    .clickable(enabled = state.canSave) { viewModel.save(context) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        text = "Save post",
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
                textStyle = MaterialTheme.typography.headlineSmall.copy(
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

            if (state.isLinkFieldVisible) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    EditField(
                        value = state.linkUrl,
                        onValueChange = viewModel::onLinkUrlChange,
                        placeholder = "Paste your link here",
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                        ),
                        singleLine = true,
                    )
                }
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
                            model = item.feedUrl,
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

        // ---- Toolbar ----
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
                imageVector = CommunityIcons.EditPost,
                contentDescription = "Add link",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(26.dp)
                    .clickable { viewModel.toggleLinkField() },
            )
            Spacer(Modifier.width(20.dp))
            Icon(
                imageVector = CommunityIcons.Image,
                contentDescription = "Add photos or videos",
                tint = if (state.canAddMore) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier
                    .size(26.dp)
                    .clickable(enabled = state.canAddMore) {
                        mediaPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    },
            )
        }
    }
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

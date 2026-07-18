package com.example.kinetixfsl.community

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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.kinetixfsl.ui.theme.KinetixError
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme
import com.example.kinetixfsl.ui.theme.KinetixIndigo
import com.example.kinetixfsl.ui.theme.KinetixInk
import com.example.kinetixfsl.ui.theme.KinetixMuted
import com.example.kinetixfsl.ui.theme.KinetixOutline
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
    onSelectCommunity: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreatePostViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (state.isPostCreated) {
        onClose()
        viewModel.onPostCreatedHandled()
    }

    // Photo picker launchers — the modern Android way (no permissions needed).
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.onImagePicked(uri)
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.onVideoPicked(uri)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KinetixWhite),
    ) {
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
                tint = KinetixInk,
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onClose),
            )

            Spacer(Modifier.width(12.dp))

            // "Select Community" pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(KinetixIndigo)
                    .clickable(onClick = onSelectCommunity)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Select Community",
                    style = MaterialTheme.typography.labelLarge,
                    color = KinetixWhite,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.weight(1f))

            // Post button — disabled while uploading or title is empty.
            val canPost = state.title.isNotBlank() && !state.isUploading
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (canPost) KinetixIndigo else KinetixOutline)
                    .clickable(enabled = canPost) { viewModel.submitPost(context) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                if (state.isUploading) {
                    CircularProgressIndicator(
                        color = KinetixWhite,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(
                        text = "Post",
                        style = MaterialTheme.typography.labelLarge,
                        color = KinetixWhite,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // ---- Error ----
        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage!!,
                style = MaterialTheme.typography.labelMedium,
                color = KinetixError,
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
                    color = KinetixInk,
                ),
            )

            Spacer(Modifier.height(8.dp))

            // Body field
            PlainTextField(
                value = state.body,
                onValueChange = viewModel::onBodyChange,
                placeholder = "Body text (optional)",
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = KinetixInk),
            )


            // Link URL field (shows when user taps the link icon)
            if (state.isLinkFieldVisible) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, KinetixOutline, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    BasicTextField(
                        value = state.linkUrl,
                        onValueChange = viewModel::onLinkUrlChange,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = KinetixIndigo),
                        cursorBrush = SolidColor(KinetixIndigo),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box {
                                if (state.linkUrl.isEmpty()) {
                                    Text(
                                        "Paste your link here",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = KinetixMuted,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Media preview (if the user has picked something).
            if (state.mediaUri != null) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = state.mediaUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, KinetixOutline, RoundedCornerShape(12.dp)),
                    )

                    // Small X to remove the media.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(KinetixInk.copy(alpha = 0.6f))
                            .clickable { viewModel.clearMedia() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = CloseIcon,
                            contentDescription = "Remove",
                            tint = KinetixWhite,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    // Label if it's a video.
                    if (state.mediaType == "video") {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(KinetixInk.copy(alpha = 0.6f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "Video",
                                style = MaterialTheme.typography.labelMedium,
                                color = KinetixWhite,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // ---- Bottom toolbar: link, image, video ----
        HorizontalDivider(color = KinetixOutline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KinetixWhite)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarIcon(
                icon = LinkIcon,
                description = "Add link",
                onClick = { viewModel.toggleLinkField() },
            )
            Spacer(Modifier.width(20.dp))
            ToolbarIcon(
                icon = ImageIcon,
                description = "Add image",
                onClick = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )
            Spacer(Modifier.width(20.dp))
            ToolbarIcon(
                icon = VideoIcon,
                description = "Add video",
                onClick = {
                    videoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
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
        cursorBrush = SolidColor(KinetixIndigo),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle.copy(color = KinetixMuted),
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
) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = KinetixInk,
        modifier = Modifier
            .size(28.dp)
            .clickable(onClick = onClick),
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

private val LinkIcon: ImageVector by lazy {
    ImageVector.Builder("Link", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
            moveTo(10f, 14f); lineTo(14f, 10f)
            moveTo(8f, 12f)
            curveTo(5f, 9f, 5f, 5f, 8f, 5f)
            lineTo(10f, 3f)
            curveTo(13f, 3f, 13f, 7f, 10f, 7f)
            moveTo(14f, 17f)
            curveTo(17f, 17f, 17f, 21f, 14f, 21f)
            lineTo(12f, 19f)
            curveTo(9f, 19f, 9f, 15f, 12f, 15f)
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
            onSelectCommunity = {},
        )
    }
}

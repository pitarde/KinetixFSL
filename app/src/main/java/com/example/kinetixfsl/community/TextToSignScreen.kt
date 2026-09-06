package com.example.kinetixfsl.community

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.kinetixfsl.ui.theme.KinetixPageBackground

/**
 * Text-to-Sign: a search that turns a typed word into the sign for it.
 *
 * The user types a word (e.g. "salamat") and sees the photos and videos of that
 * sign, pulled from admin-validated community posts whose #hashtags contain the
 * word. Only the media is shown — no title, votes or comments — so the screen
 * reads as a dictionary of signs rather than a feed.
 *
 * When the app has no validated post for a word yet, the default tutorials
 * (trained signs and admin uploads) will fill in — that content is added later;
 * for now an empty result simply says nothing was found.
 */
@Composable
fun TextToSignScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TextToSignViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // A tapped result opens full screen (image fit, or a playing video).
    var opened by remember { mutableStateOf<SignMedia?>(null) }

    // Same dark bar treatment as the Home Feed's own top bar — KinetixNavy in
    // light mode, colorScheme.surface in dark — so the status bar's
    // notification area reads as part of one continuous colored bar. Always
    // wants light (white) status bar icons regardless of the app's theme.
    com.example.kinetixfsl.ui.theme.StatusBarLightIcons(light = true)
    val darkTheme = isSystemInDarkTheme()
    val barBackground = if (darkTheme) MaterialTheme.colorScheme.surface else com.example.kinetixfsl.ui.theme.KinetixNavy
    val barContentColor = if (darkTheme) MaterialTheme.colorScheme.onSurface else com.example.kinetixfsl.ui.theme.KinetixWhite

    BackHandler(onBack = onClose)

    val pageBackground = if (darkTheme) {
        MaterialTheme.colorScheme.background
    } else {
        KinetixPageBackground
    }

    Box(modifier = modifier.fillMaxSize().background(pageBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            // ---- Top bar: back + title ----
            // The background is painted on this Box, which takes the status
            // bar inset as padding rather than being pushed below it — so the
            // color runs all the way to the physical top of the screen.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(barBackground)
                    .statusBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = CommunityIcons.ArrowBack,
                        contentDescription = "Back",
                        tint = barContentColor,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClose)
                            .padding(8.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Text to Gesture",
                        style = MaterialTheme.typography.titleLarge,
                        color = barContentColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // ---- Search field ----
            SearchField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                onSubmit = viewModel::submitSearch,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // ---- Results / states ----
            when {
                state.isLoading -> CenteredBox {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                state.query.isBlank() -> HintState(
                    title = "Type a word you want to know",
                )

                state.hasSearched && state.results.isEmpty() -> HintState(
                    title = "No signs found for “${state.query.trim()}”",
                    body = "No validated tutorial uses that word as a #hashtag yet. Default tutorials will fill these in soon.",
                )

                else -> ResultsGrid(
                    results = state.results,
                    onOpen = { opened = it },
                )
            }
        }
    }

    opened?.let { sign ->
        FullScreenMediaViewer(
            imageUrl = sign.media.url.takeIf { !sign.media.isVideo },
            videoUrl = sign.media.url.takeIf { sign.media.isVideo },
            onClose = { opened = null },
        )
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Same styling as the Home Feed's own inline search bar — primaryContainer
    // fill, no border — so the two read as the same design.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CommunityIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = "Search a word…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            Icon(
                imageVector = CommunityIcons.Close,
                contentDescription = "Clear",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onValueChange("") },
            )
        }
    }
}

@Composable
private fun ResultsGrid(
    results: List<SignMedia>,
    onOpen: (SignMedia) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(results, key = { "${it.postId}-${it.media.url}" }) { sign ->
            SignCell(sign = sign, onClick = { onOpen(sign) })
        }
    }
}

@Composable
private fun SignCell(
    sign: SignMedia,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick),
    ) {
        // A video's feedUrl is its generated still, so one image request covers
        // both types for the thumbnail; the play badge marks the videos.
        AsyncImage(
            model = mediaUrl(sign.media.feedUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (sign.media.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = PlayIcon,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun HintState(title: String, body: String? = null) {
    CenteredBox {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            Icon(
                imageVector = CommunityIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (body != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Filled play triangle for the video badge on a result cell. */
private val PlayIcon: ImageVector by lazy {
    ImageVector.Builder("Play", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close()
        }
    }.build()
}

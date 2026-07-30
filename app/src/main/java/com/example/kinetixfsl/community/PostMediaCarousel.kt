package com.example.kinetixfsl.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.kinetixfsl.community.model.PostMedia

/**
 * Swipeable media for a post: one item at a time, dots underneath, and a
 * "3/10" counter in the corner when there's more than one.
 *
 * Only the item on screen plays — [isActive] gates the whole carousel (the feed
 * uses it for scroll visibility) and the pager's current page gates which video
 * inside it runs, so ten attached videos never decode at once.
 */
@Composable
internal fun PostMediaCarousel(
    media: List<PostMedia>,
    isActive: Boolean,
    onMediaClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** The post's generated still, used as the poster for video pages. */
    posterUrl: String? = null,
    /** Base64 blur for the first item, painted instantly while media loads. */
    blurData: String? = null,
    height: Dp = 260.dp,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    showBorder: Boolean = true,
) {
    if (media.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { media.size })

    Box(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            // A little room so a peek of the next item hints at swipeability.
            pageSpacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) { page ->
            val item = media[page]
            val pageModifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .then(
                    if (showBorder) {
                        Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            shape,
                        )
                    } else {
                        Modifier
                    }
                )

            // Only the first page can reuse the post-level blur and still.
            val pageBlur = blurData?.takeIf { page == 0 }

            Box(modifier = pageModifier) {
                // Tier 1: paints immediately, no request of its own.
                BlurPlaceholder(
                    data = pageBlur,
                    modifier = Modifier.fillMaxSize(),
                )

                if (item.isVideo) {
                    FeedVideoPlayer(
                        videoUrl = item.url,
                        isVisible = isActive && pagerState.currentPage == page,
                        onClick = { onMediaClick(page) },
                        modifier = Modifier.fillMaxSize(),
                        posterUrl = posterUrl?.takeIf { page == 0 },
                        // Let the blur show through until there's a frame.
                        opaqueBackground = pageBlur == null,
                    )
                } else {
                    // Tier 2: the light feed copy, not the full-resolution file.
                    AsyncImage(
                        model = item.feedUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onMediaClick(page) },
                    )
                }
            }
        }

        if (media.size > 1) {
            MediaCounter(
                current = pagerState.currentPage + 1,
                total = media.size,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
            )
            PagerDots(
                pagerState = pagerState,
                count = media.size,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
            )
        }
    }
}

/** "3/10" pill in the corner, the way Instagram marks a multi-image post. */
@Composable
private fun MediaCounter(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = "$current/$total",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PagerDots(
    pagerState: PagerState,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = pagerState.currentPage == index
            Box(
                modifier = Modifier
                    .size(if (selected) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = 0.45f)
                        }
                    ),
            )
        }
    }
}

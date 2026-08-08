package com.example.kinetixfsl.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's skeleton-loading toolkit: a shimmer modifier, a few primitives, and
 * composite placeholders that mirror the real cards (post, community, profile,
 * notification, conversation).
 *
 * Why skeletons over a spinner: they show the *shape* of what's coming, so the
 * screen never jumps when data lands — every placeholder here is sized to the
 * real component it stands in for. Everything reads from `MaterialTheme`, so it
 * follows light and dark automatically, and uses relative widths so it holds up
 * across screen sizes.
 */

// ---------------------------------------------------------------------------
// Shimmer
// ---------------------------------------------------------------------------

/**
 * A single shimmer transition shared by every placeholder on screen, so a page
 * full of skeletons pulses in unison rather than each block animating on its
 * own phase — and so there's one animation running, not dozens.
 *
 * The band sweeps left to right forever; [progress] is 0..1 across one sweep.
 */
@Composable
private fun rememberShimmerProgress(): Float {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    return progress
}

/**
 * Paints a shimmering placeholder fill, clipped to [shape].
 *
 * The gradient is drawn in `drawWithCache` off the animation value rather than
 * as a recomposing background, so the sweep is a pure draw-phase update — no
 * recomposition per frame, which is what keeps a wall of skeletons cheap.
 */
@Composable
fun Modifier.shimmer(shape: Shape = RoundedCornerShape(6.dp)): Modifier {
    val progress = rememberShimmerProgress()
    return this.shimmerFill(progress, shape)
}

private fun Modifier.shimmerFill(progress: Float, shape: Shape): Modifier =
    this
        .clip(shape)
        .drawWithCache {
            // Base and highlight are resolved from the theme at draw time. Using
            // a translucent white highlight over the base means it reads
            // correctly on both light and dark surfaces without a second palette.
            val base = ShimmerColors.base
            val highlight = ShimmerColors.highlight
            val bandWidth = size.width * 0.6f
            // Travel from fully off the left to fully off the right.
            val start = -bandWidth + (size.width + bandWidth) * progress
            val brush = Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(start, 0f),
                end = Offset(start + bandWidth, 0f),
            )
            onDrawBehind { drawRect(brush) }
        }

/**
 * Theme-derived shimmer colors. Filled in by [ProvideShimmerColors] so the
 * draw code can read them without a Composable scope; falls back to sensible
 * defaults if that wrapper is ever missed.
 */
internal object ShimmerColors {
    var base: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFE7E8F0)
    var highlight: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFF4F5FA)
}

/**
 * Feeds the current theme's surface colors into [ShimmerColors]. Rendered once
 * near the top of any screen that shows skeletons — or simply relied upon to
 * fall back. Kept as a plain side-effecting composable so the draw-phase
 * shimmer can read flat values.
 */
@Composable
fun SyncShimmerColors() {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    ShimmerColors.base = scheme.surfaceVariant
    // A highlight lifted toward the surface color reads as a sheen on both themes.
    ShimmerColors.highlight = scheme.surface.copy(alpha = 0.85f)
        .compositeOverSurfaceVariant(scheme.surfaceVariant)
}

private fun androidx.compose.ui.graphics.Color.compositeOverSurfaceVariant(
    under: androidx.compose.ui.graphics.Color,
): androidx.compose.ui.graphics.Color {
    val a = alpha
    return androidx.compose.ui.graphics.Color(
        red = red * a + under.red * (1 - a),
        green = green * a + under.green * (1 - a),
        blue = blue * a + under.blue * (1 - a),
        alpha = 1f,
    )
}

// ---------------------------------------------------------------------------
// Primitives
// ---------------------------------------------------------------------------

/** A shimmering rounded rectangle — the building block for every placeholder. */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    Box(modifier.shimmer(shape))
}

/** A shimmering circle — avatars and icon slots. */
@Composable
fun SkeletonCircle(size: Dp) {
    Box(
        Modifier
            .size(size)
            .shimmer(CircleShape),
    )
}

/** One line of "text". [widthFraction] varies the length so blocks look natural. */
@Composable
fun SkeletonLine(
    widthFraction: Float = 1f,
    height: Dp = 12.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .shimmer(RoundedCornerShape(4.dp)),
    )
}

// ---------------------------------------------------------------------------
// Composite skeletons — one per real component
// ---------------------------------------------------------------------------

/**
 * Stands in for a feed [com.example.kinetixfsl.community.PostCard]: author row,
 * title, two body lines, a media block, and the interaction bar. Same 16dp
 * padding and spacing as the real card, so the swap to real content doesn't
 * shift anything.
 */
@Composable
fun PostCardSkeleton(showMedia: Boolean = true) {
    SyncShimmerColors()
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        // Author row.
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonCircle(32.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                SkeletonLine(widthFraction = 0.4f, height = 12.dp)
                Spacer(Modifier.height(6.dp))
                SkeletonLine(widthFraction = 0.25f, height = 10.dp)
            }
        }
        Spacer(Modifier.height(14.dp))
        // Title.
        SkeletonLine(widthFraction = 0.7f, height = 16.dp)
        Spacer(Modifier.height(10.dp))
        // Body.
        SkeletonLine(widthFraction = 1f, height = 12.dp)
        Spacer(Modifier.height(6.dp))
        SkeletonLine(widthFraction = 0.9f, height = 12.dp)
        if (showMedia) {
            Spacer(Modifier.height(12.dp))
            SkeletonBox(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(12.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        // Interaction bar: up / down / comment on the left, share on the right.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SkeletonBox(
                Modifier.width(140.dp).height(30.dp),
                shape = RoundedCornerShape(50),
            )
            SkeletonBox(
                Modifier.width(56.dp).height(30.dp),
                shape = RoundedCornerShape(50),
            )
        }
    }
}

/** A column of [PostCardSkeleton]s with dividers — a whole loading feed. */
@Composable
fun FeedSkeleton(count: Int = 4) {
    Column(Modifier.fillMaxWidth()) {
        repeat(count) {
            PostCardSkeleton(showMedia = it % 2 == 0)
            androidx.compose.material3.HorizontalDivider(
                color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/**
 * Stands in for a [com.example.kinetixfsl.community.discover.CommunityCard]:
 * avatar, name, member count, Join pill, and a two-line blurb, inside the same
 * bordered 16dp card.
 */
@Composable
fun CommunityCardSkeleton() {
    SyncShimmerColors()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonCircle(48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                SkeletonLine(widthFraction = 0.6f, height = 14.dp)
                Spacer(Modifier.height(6.dp))
                SkeletonLine(widthFraction = 0.3f, height = 10.dp)
            }
            Spacer(Modifier.width(12.dp))
            SkeletonBox(
                Modifier.width(64.dp).height(30.dp),
                shape = RoundedCornerShape(50),
            )
        }
        Spacer(Modifier.height(12.dp))
        SkeletonLine(widthFraction = 1f, height = 11.dp)
        Spacer(Modifier.height(6.dp))
        SkeletonLine(widthFraction = 0.8f, height = 11.dp)
    }
}

/** A stack of community-card skeletons — the Discover / category loading list. */
@Composable
fun CommunityListSkeleton(count: Int = 5) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(count) { CommunityCardSkeleton() }
    }
}

/**
 * Stands in for a [com.example.kinetixfsl.community.inbox] conversation row and
 * a notification row alike — avatar, a name line and a preview line, with a
 * short trailing block for the timestamp. Both lists share this shape.
 */
@Composable
fun InboxRowSkeleton() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonCircle(50.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            SkeletonLine(widthFraction = 0.45f, height = 13.dp)
            Spacer(Modifier.height(7.dp))
            SkeletonLine(widthFraction = 0.75f, height = 11.dp)
        }
        Spacer(Modifier.width(10.dp))
        SkeletonBox(Modifier.width(32.dp).height(10.dp))
    }
}

/** A list of inbox-row skeletons — the Chat and Notification tabs while loading. */
@Composable
fun InboxListSkeleton(count: Int = 8) {
    SyncShimmerColors()
    Column(Modifier.fillMaxWidth()) {
        repeat(count) {
            InboxRowSkeleton()
            androidx.compose.material3.HorizontalDivider(
                color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = 78.dp),
            )
        }
    }
}

/**
 * Stands in for an open conversation while its messages load: a run of chat
 * bubbles, alternating sides and widths so it reads as a thread rather than a
 * list. Incoming bubbles sit left on the surface tint, outgoing sit right —
 * the same sides the real bubbles use, so nothing jumps when they arrive.
 */
@Composable
fun ChatSkeleton(count: Int = 7) {
    SyncShimmerColors()
    // A fixed pattern rather than random, so it looks the same across recompositions
    // — mine / theirs, and a width bucket for each.
    val pattern = listOf(
        false to 0.55f,
        true to 0.35f,
        false to 0.7f,
        true to 0.5f,
        false to 0.4f,
        true to 0.6f,
        false to 0.45f,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(count) { i ->
            val (isMine, widthFraction) = pattern[i % pattern.size]
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
            ) {
                // The corner nearest the sender is squared off, matching the
                // real bubble's shape cue.
                val shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMine) 16.dp else 4.dp,
                    bottomEnd = if (isMine) 4.dp else 16.dp,
                )
                SkeletonBox(
                    Modifier
                        .fillMaxWidth(widthFraction)
                        .height(if (i % 3 == 0) 56.dp else 36.dp),
                    shape = shape,
                )
            }
        }
    }
}

/**
 * Stands in for the profile header — cover banner, the avatar straddling its
 * edge, the name, a stat row, and the first couple of post cards below.
 */
@Composable
fun ProfileSkeleton() {
    SyncShimmerColors()
    Column(Modifier.fillMaxWidth()) {
        // Cover banner.
        SkeletonBox(
            Modifier.fillMaxWidth().height(130.dp),
            shape = RoundedCornerShape(0.dp),
        )
        Column(Modifier.padding(16.dp)) {
            SkeletonCircle(72.dp)
            Spacer(Modifier.height(12.dp))
            SkeletonLine(widthFraction = 0.5f, height = 18.dp)
            Spacer(Modifier.height(8.dp))
            SkeletonLine(widthFraction = 0.35f, height = 12.dp)
            Spacer(Modifier.height(16.dp))
            // Stat row: three short blocks.
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                repeat(3) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SkeletonLine(widthFraction = 1f, height = 14.dp, modifier = Modifier.width(40.dp))
                        Spacer(Modifier.height(6.dp))
                        SkeletonBox(Modifier.width(56.dp).height(10.dp))
                    }
                }
            }
        }
        androidx.compose.material3.HorizontalDivider(
            color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
        )
        PostCardSkeleton(showMedia = true)
    }
}

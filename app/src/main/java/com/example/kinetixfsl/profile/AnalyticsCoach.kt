package com.example.kinetixfsl.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Tab 4 — Coach's Picks (prescriptive analytics: "what to do about it").
 *
 * A recommended practice queue built from decay + confusion data, a targeted
 * weak-spot drill, a best-time insight, and an adaptive-difficulty explainer.
 * All CTAs are no-ops for now — they hook into real drills once progress works.
 */
@Composable
fun CoachTab(
    data: AnalyticsData,
    onStartDrill: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {

        // ── Gradient hero: today's queue ────────────────────────────
        RecommendedQueueHero(data.recommendedQueue)

        CardGap()

        // ── Fix your weak spot ──────────────────────────────────────
        AnalyticsCard {
            CardHeader(
                icon = ProfileIcons.Target,
                accent = AccentCoach,
                title = "Fix your weak spot",
                subtitle = "Targeted from your error data",
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = data.fixWeakSpotStat,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(14.dp))
            CtaButton(
                label = data.fixWeakSpotCta,
                accent = AccentCoach,
                onClick = onStartDrill,
            )
        }

        CardGap()

        // ── Best time to practice ───────────────────────────────────
        AnalyticsCard {
            CardHeader(
                icon = ProfileIcons.Clock,
                accent = AccentCoach,
                title = "Best time to practice",
                subtitle = "When you perform best",
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = data.bestTimeInsight,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        CardGap()

        // ── Adaptive difficulty ─────────────────────────────────────
        AnalyticsCard {
            CardHeader(
                icon = ProfileIcons.Bolt,
                accent = AccentCoach,
                title = "Adaptive difficulty",
                subtitle = "How lessons will adjust to you",
            )
            Spacer(Modifier.height(14.dp))
            data.adaptiveLessons.forEachIndexed { i, item ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                AdaptiveRow(item)
            }
        }
    }
}

// ── Gradient hero card ──────────────────────────────────────────────

@Composable
private fun RecommendedQueueHero(queue: List<QueueItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF7C4DFF), Color(0xFFA55EEA), Color(0xFF6C5CE7)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ProfileIcons.Wand,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    text = "Today's recommended queue",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Auto-picked from your weak spots & decay risk",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        queue.forEachIndexed { i, item ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            QueueRow(index = i + 1, item = item)
        }
    }
}

@Composable
private fun QueueRow(index: Int, item: QueueItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF6C5CE7),
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = item.reason,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        Icon(
            imageVector = ProfileIcons.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── CTA button ──────────────────────────────────────────────────────

@Composable
private fun CtaButton(label: String, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.size(6.dp))
        Icon(
            imageVector = ProfileIcons.ChevronRight,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── Adaptive difficulty row ─────────────────────────────────────────

@Composable
private fun AdaptiveRow(item: AdaptiveItem) {
    val up = item.trend == Trend.UP
    val color = if (up) AccentForecast else AccentWeak
    val icon: ImageVector = if (up) ProfileIcons.ArrowUp else ProfileIcons.ArrowDown

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.module,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = if (up) "Speeding up" else "Slowing down",
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = item.reason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

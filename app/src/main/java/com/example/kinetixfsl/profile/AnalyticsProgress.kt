package com.example.kinetixfsl.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Tab 1 — Progress (descriptive analytics: "what happened so far").
 *
 * Weekly signs-learned bars, per-category mastery meters, and a GitHub-style
 * practice-consistency heatmap.
 */
@Composable
fun ProgressTab(
    data: AnalyticsData,
    modifier: Modifier = Modifier,
) {
    val categoryMastery = data.categoryMastery
    Column(modifier = modifier.fillMaxWidth()) {

        // ── Weekly activity ─────────────────────────────────────────
        AnalyticsCard {
            CardHeader(
                icon = ProfileIcons.TrendingUp,
                accent = AccentProgress,
                title = "This week",
                subtitle = "Signs learned per day",
            )
            Spacer(Modifier.height(16.dp))
            WeeklyBars(data.weeklyBars)
        }

        CardGap()

        // ── Category mastery ────────────────────────────────────────
        AnalyticsCard {
            CardHeader(
                icon = ProfileIcons.HandStar,
                accent = AccentProgress,
                title = "Category mastery",
                subtitle = "How complete each module is",
            )
            Spacer(Modifier.height(14.dp))
            categoryMastery.forEachIndexed { i, cat ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                MeterRow(
                    label = cat.name,
                    fraction = cat.percent,
                    valueText = "${(cat.percent * 100).toInt()}%",
                    accent = AccentProgress,
                )
            }
        }

        CardGap()

        // ── Consistency heatmap ─────────────────────────────────────
        AnalyticsCard {
            CardHeader(
                icon = ProfileIcons.Flame,
                accent = AccentProgress,
                title = "Practice consistency",
                subtitle = "Last 5 weeks",
            )
            Spacer(Modifier.height(14.dp))
            Heatmap(data.heatmap, AccentProgress)
        }
    }
}

// ── Weekly bars ─────────────────────────────────────────────────────

@Composable
private fun WeeklyBars(bars: List<DayBar>) {
    val maxCount = (bars.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    val accent = AccentProgress
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val n = bars.size
            val slot = size.width / n
            val barW = slot * 0.5f
            val radius = CornerRadius(barW / 2f, barW / 2f)
            bars.forEachIndexed { i, bar ->
                val cx = slot * i + slot / 2f
                val left = cx - barW / 2f
                // Track (full height, faint) so short days still read as bars.
                drawRoundRect(
                    color = track,
                    topLeft = Offset(left, 0f),
                    size = Size(barW, size.height),
                    cornerRadius = radius,
                )
                val h = size.height * (bar.count.toFloat() / maxCount)
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(left, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = radius,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            bars.forEach { bar ->
                Text(
                    text = bar.label.first().toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ── Heatmap ─────────────────────────────────────────────────────────

@Composable
private fun Heatmap(weeks: List<List<Int>>, accent: Color) {
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    val emptyCell = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)

    Column {
        // Day-of-week header
        Row(modifier = Modifier.fillMaxWidth()) {
            dayLabels.forEach { d ->
                Text(
                    text = d,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        weeks.forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
            ) {
                week.forEach { intensity ->
                    val color = if (intensity <= 0) {
                        emptyCell
                    } else {
                        // 4 levels of alpha from faint to solid.
                        accent.copy(alpha = 0.25f + 0.25f * (intensity.coerceIn(1, 4) - 1))
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 3.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(color),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Legend: Less → More
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Less",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(6.dp))
            listOf(0, 1, 2, 3, 4).forEach { level ->
                val color = if (level == 0) {
                    emptyCell
                } else {
                    accent.copy(alpha = 0.25f + 0.25f * (level - 1))
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
            }
            Spacer(Modifier.size(6.dp))
            Text(
                text = "More",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

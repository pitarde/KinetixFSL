package com.example.kinetixfsl.community.moderator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kinetixfsl.community.compact
import com.example.kinetixfsl.community.model.Post
import com.example.kinetixfsl.profile.AccentCoach
import com.example.kinetixfsl.profile.AccentForecast
import com.example.kinetixfsl.profile.AccentProgress
import com.example.kinetixfsl.profile.AccentWeak
import com.example.kinetixfsl.profile.AnalyticsCard
import com.example.kinetixfsl.profile.CardGap
import com.example.kinetixfsl.profile.CardHeader
import com.example.kinetixfsl.profile.MeterRow
import com.example.kinetixfsl.profile.MeterTrack
import com.example.kinetixfsl.profile.ProfileIcons
import kotlin.math.min

/**
 * The moderator's own content analytics, in the spirit of YouTube Studio's
 * Analytics tab — reached by opening [EligibilityScreen] once approved.
 * Built entirely from the same charts-and-meters visual language as the
 * learner-facing Profile analytics (see `profile/Analytics*.kt`), across the
 * same four lenses, named for content rather than raw BI jargon: Performance
 * (descriptive), Insights (diagnostic), Forecast (predictive) and
 * Recommendations (prescriptive).
 */
@Composable
internal fun ModeratorAnalyticsContent(
    modifier: Modifier = Modifier,
    viewModel: ModeratorAnalyticsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is ModeratorAnalyticsUiState.Loading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        is ModeratorAnalyticsUiState.Ready -> if (!current.hasContent) {
            EmptyAnalyticsState(modifier)
        } else {
            AnalyticsList(current, modifier)
        }
    }
}

@Composable
private fun EmptyAnalyticsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = ProfileIcons.TrendingUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No analytics yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Once you publish and it's validated, your content as a moderator " +
                "will show up here with charts across what happened, why, what's next, " +
                "and what to do about it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AnalyticsList(state: ModeratorAnalyticsUiState.Ready, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    ) {
        item {
            Text(
                text = state.asOfDate,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
        }

        // ── Performance (descriptive) ───────────────────────────────
        item {
            AnalyticsCard {
                CardHeader(
                    icon = ProfileIcons.TrendingUp,
                    accent = AccentProgress,
                    title = "Performance",
                    subtitle = "What happened",
                )
                Spacer(Modifier.height(16.dp))
                StatGrid(state.descriptive)
                if (state.descriptive.postSeries.size >= 2) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "Net score per post (most recent ${state.descriptive.postSeries.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    PostBarChart(state.descriptive.postSeries, AccentProgress)
                }
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EngagementGauge(
                        percent = (state.descriptive.avgEngagementRate * 100).toInt().coerceIn(0, 100),
                        accent = AccentProgress,
                        modifier = Modifier.size(96.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "Average engagement rate — (upvotes + comments + shares) ÷ views, per post.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item { CardGapItem() }

        // ── Insights (diagnostic) ───────────────────────────────────
        val hasDiagnostics = state.mediaComparison != null ||
            state.communityBreakdown.isNotEmpty() ||
            state.hashtagCoverage != null
        if (hasDiagnostics) {
            item {
                AnalyticsCard {
                    CardHeader(
                        icon = ProfileIcons.AlertTriangle,
                        accent = AccentWeak,
                        title = "Insights",
                        subtitle = "Why it's happening",
                    )
                    Spacer(Modifier.height(16.dp))

                    state.mediaComparison?.let { media ->
                        Text(
                            text = "Images vs. video",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(10.dp))
                        MediaComparisonBars(media)
                        Spacer(Modifier.height(18.dp))
                    }

                    if (state.communityBreakdown.isNotEmpty()) {
                        Text(
                            text = "Best-performing destination",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(10.dp))
                        val maxAvg = state.communityBreakdown.maxOf { it.avgScore }.coerceAtLeast(1.0)
                        state.communityBreakdown.forEachIndexed { i, c ->
                            if (i > 0) Spacer(Modifier.height(10.dp))
                            MeterRow(
                                label = c.name,
                                fraction = (c.avgScore / maxAvg).toFloat(),
                                valueText = "%.1f".format(c.avgScore),
                                accent = AccentWeak,
                                labelWidth = 96.dp,
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                    }

                    state.hashtagCoverage?.let { coverage ->
                        Text(
                            text = "Hashtag coverage",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        CoverageBar(coverage, AccentWeak)
                    }
                }
            }
            item { CardGapItem() }
        }

        // ── Forecast (predictive) ───────────────────────────────────
        state.predictive?.let { predictive ->
            item {
                AnalyticsCard {
                    CardHeader(
                        icon = ProfileIcons.Sparkles,
                        accent = AccentForecast,
                        title = "Forecast",
                        subtitle = "What's likely next",
                    )
                    Spacer(Modifier.height(16.dp))
                    CumulativeLineChart(predictive.actualSeries, predictive.projectedSeries, AccentForecast)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LegendDash(AccentForecast, solid = true)
                        Spacer(Modifier.size(6.dp))
                        Text("So far", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(16.dp))
                        LegendDash(AccentForecast, solid = false)
                        Spacer(Modifier.size(6.dp))
                        Text("Projected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "~${predictive.projectedUpvotes30d} upvotes / 30d",
                            style = MaterialTheme.typography.labelMedium,
                            color = AccentForecast,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    TrendRow(predictive)
                }
            }
            item { CardGapItem() }
        }

        // ── Recommendations (prescriptive) ──────────────────────────
        item { RecommendationsHero(state.recommendations) }

        if (state.topPosts.isNotEmpty()) {
            item { Spacer(Modifier.height(16.dp)) }
            item {
                Text(
                    text = "Top content",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            items(state.topPosts, key = { it.id }) { post ->
                TopPostRow(post, maxScore = state.topPosts.maxOf { it.score.coerceAtLeast(1) })
                Spacer(Modifier.height(8.dp))
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CardGapItem() { CardGap() }

// ── Performance: stat grid ────────────────────────────────────────────

@Composable
private fun StatGrid(stats: DescriptiveStats) {
    val tiles = listOf(
        Triple(ProfileIcons.HandStar, "Posts", stats.postCount.toLong().compact()),
        Triple(ProfileIcons.TrendingUp, "Views", stats.totalViews.compact()),
        Triple(ProfileIcons.ArrowUp, "Upvotes", stats.totalUpvotes.compact()),
        Triple(ProfileIcons.ArrowDown, "Downvotes", stats.totalDownvotes.compact()),
        Triple(ProfileIcons.Bolt, "Comments", stats.totalComments.compact()),
        Triple(ProfileIcons.Sparkles, "Shares", stats.totalShares.compact()),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (icon, label, value) ->
                    Box(modifier = Modifier.weight(1f)) {
                        StatTile(icon, label, value)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(AccentProgress.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = AccentProgress, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Performance: bar chart ────────────────────────────────────────────

@Composable
private fun PostBarChart(points: List<PostPoint>, accent: Color) {
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
    val maxScore = (points.maxOfOrNull { it.score.coerceAtLeast(0) } ?: 1L).coerceAtLeast(1L)

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val n = points.size
            val slot = size.width / n
            val barW = slot * 0.5f
            val radius = CornerRadius(barW / 3f, barW / 3f)
            points.forEachIndexed { i, point ->
                val cx = slot * i + slot / 2f
                val left = cx - barW / 2f
                drawRoundRect(
                    color = track,
                    topLeft = Offset(left, 0f),
                    size = Size(barW, size.height),
                    cornerRadius = radius,
                )
                val h = size.height * (point.score.coerceAtLeast(0).toFloat() / maxScore)
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(left, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = radius,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEach { point ->
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ── Performance: circular engagement gauge ────────────────────────────

@Composable
private fun EngagementGauge(percent: Int, accent: Color, modifier: Modifier = Modifier) {
    val fraction = (percent / 100f).coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 12f
            val diameter = min(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent,
                startAngle = 135f,
                sweepAngle = 270f * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$percent%", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold)
            Text("engaged", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Insights: media comparison paired bars ────────────────────────────

@Composable
private fun MediaComparisonBars(media: MediaComparison) {
    val maxAvg = maxOf(media.imageAvgScore, media.videoAvgScore).coerceAtLeast(1.0)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MeterRow(
            label = "Images (${media.imageCount})",
            fraction = (media.imageAvgScore / maxAvg).toFloat(),
            valueText = "%.1f".format(media.imageAvgScore),
            accent = AccentWeak,
            labelWidth = 96.dp,
        )
        MeterRow(
            label = "Video (${media.videoCount})",
            fraction = (media.videoAvgScore / maxAvg).toFloat(),
            valueText = "%.1f".format(media.videoAvgScore),
            accent = AccentForecast,
            labelWidth = 96.dp,
        )
    }
}

// ── Insights: hashtag coverage bar ─────────────────────────────────────

@Composable
private fun CoverageBar(coverage: HashtagCoverage, accent: Color) {
    val fraction = if (coverage.total > 0) coverage.tagged.toFloat() / coverage.total else 0f
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${coverage.tagged} of ${coverage.total} posts tagged",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        Spacer(Modifier.height(6.dp))
        MeterTrack(fraction = fraction, accent = accent, height = 10.dp)
    }
}

// ── Forecast: cumulative line chart ────────────────────────────────────

@Composable
private fun CumulativeLineChart(actual: List<Long>, projected: List<Long>, accent: Color) {
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
    val allValues = actual + projected.drop(1)
    val maxV = (allValues.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
    val totalPoints = allValues.size

    Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
        val leftPad = 4f
        val bottomPad = 6f
        val topPad = 6f
        val chartW = size.width - leftPad
        val chartH = size.height - bottomPad - topPad

        fun pointAt(index: Int, value: Long): Offset {
            val x = leftPad + chartW * (index.toFloat() / (totalPoints - 1).coerceAtLeast(1))
            val y = topPad + chartH * (1f - value / maxV)
            return Offset(x, y)
        }

        for (g in 0..2) {
            val y = topPad + chartH * (g / 2f)
            drawLine(grid, Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1.5f)
        }

        val actualPoints = actual.mapIndexed { i, v -> pointAt(i, v) }
        val startIdx = actual.size - 1
        val projectedPoints = projected.mapIndexed { i, v -> pointAt(startIdx + i, v) }

        if (projectedPoints.size >= 2) {
            val area = Path().apply {
                moveTo(projectedPoints.first().x, topPad + chartH)
                projectedPoints.forEach { lineTo(it.x, it.y) }
                lineTo(projectedPoints.last().x, topPad + chartH)
                close()
            }
            drawPath(area, brush = Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0f))))
        }

        val actualPath = Path().apply {
            actualPoints.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        drawPath(actualPath, color = accent, style = Stroke(width = 3.5f, cap = StrokeCap.Round))

        val projPath = Path().apply {
            projectedPoints.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        drawPath(
            projPath,
            color = accent,
            style = Stroke(width = 3f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))),
        )

        actualPoints.forEach { p ->
            drawCircle(accent, radius = 5f, center = p)
            drawCircle(Color.White, radius = 2f, center = p)
        }
        projectedPoints.lastOrNull()?.let { p ->
            drawCircle(accent, radius = 5f, center = p)
            drawCircle(Color.White, radius = 2.5f, center = p)
        }
    }
}

@Composable
private fun LegendDash(color: Color, solid: Boolean) {
    if (solid) {
        Box(modifier = Modifier.size(width = 16.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(color))
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { i ->
                if (i > 0) Spacer(Modifier.size(3.dp))
                Box(modifier = Modifier.size(width = 4.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(color))
            }
        }
    }
}

@Composable
private fun TrendRow(predictive: PredictiveData) {
    val color = if (predictive.trendUp) AccentForecast else AccentWeak
    val icon = if (predictive.trendUp) ProfileIcons.ArrowUp else ProfileIcons.ArrowDown
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (predictive.trendUp) "Engagement trending up" else "Engagement trending down",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Recent avg ${"%.1f".format(predictive.recentAvgScore)} net votes vs. " +
                    "${"%.1f".format(predictive.earlierAvgScore)} earlier as a moderator.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Recommendations (prescriptive): gradient hero ────────────────────

@Composable
private fun RecommendationsHero(recommendations: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFFA55EEA), Color(0xFF6C5CE7))))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ProfileIcons.Wand, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column {
                Text("Recommendations", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text("What to do about it", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            }
        }
        Spacer(Modifier.height(16.dp))
        recommendations.forEachIndexed { i, text ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(50)).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = AccentCoach, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.size(12.dp))
                Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ── Top content row ───────────────────────────────────────────────────

@Composable
private fun TopPostRow(post: Post, maxScore: Long) {
    AnalyticsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.title.ifBlank { "(untitled post)" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                MeterTrack(
                    fraction = post.score.coerceAtLeast(0).toFloat() / maxScore,
                    accent = AccentProgress,
                    height = 6.dp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${post.score.compact()} net",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = AccentProgress,
            )
        }
    }
}

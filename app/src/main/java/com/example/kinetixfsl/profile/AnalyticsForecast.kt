package com.example.kinetixfsl.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Tab 3 — Forecast (predictive analytics: "what's likely next").
 *
 * A mastery-pace projection, a streak-risk gauge, and a skill-decay watchlist.
 */
@Composable
fun ForecastTab(data: AnalyticsData, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {

        // ── Mastery forecast ────────────────────────────────────────
        AnalyticsCard {
            CardHeader(
                icon = ProfileIcons.Sparkles,
                accent = AccentForecast,
                title = "Mastery forecast",
                subtitle = "Signs learned — projected 6 weeks",
            )
            Spacer(Modifier.height(16.dp))
            ForecastChart(
                actual = data.forecastActual,
                projected = data.forecastProjected,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendDot(AccentForecast, solid = true)
                Spacer(Modifier.size(6.dp))
                Text(
                    "So far",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(16.dp))
                LegendDot(AccentForecast, solid = false)
                Spacer(Modifier.size(6.dp))
                Text(
                    "Projected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "~${data.forecastProjected.lastOrNull() ?: 0} by week 6",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentForecast,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        CardGap()

        // ── Streak-risk gauge ───────────────────────────────────────
        AnalyticsCard {
            CardHeader(
                icon = ProfileIcons.Flame,
                accent = AccentForecast,
                title = "Streak risk",
                subtitle = "Chance you break your streak this week",
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RiskGauge(
                    percent = data.streakRiskPercent,
                    modifier = Modifier.size(120.dp),
                )
                Spacer(Modifier.size(16.dp))
                Text(
                    text = "You most often miss practice on ${data.dropOffWindow}. " +
                        "A quick session then keeps your streak alive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        CardGap()

        // ── Skill decay watch ───────────────────────────────────────
        AnalyticsCard {
            CardHeader(
                icon = ProfileIcons.Clock,
                accent = AccentForecast,
                title = "Skill decay watch",
                subtitle = "Signs fading from memory",
            )
            Spacer(Modifier.height(14.dp))
            if (data.decayWatch.isEmpty()) {
                Text(
                    text = "Learn some signs and they'll show here as they fade.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            data.decayWatch.forEachIndexed { i, item ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                DecayRow(item)
            }
        }
    }
}

// ── Forecast line chart ─────────────────────────────────────────────

@Composable
private fun ForecastChart(actual: List<Int>, projected: List<Int>) {
    val accent = AccentForecast
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
    // Combined series along a shared week axis. Actual occupies weeks
    // 0..(actual-1); projected continues from the last actual point.
    val allValues = actual + projected.drop(1)
    val maxV = (allValues.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
    val totalPoints = allValues.size

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
    ) {
        val leftPad = 4f
        val bottomPad = 6f
        val topPad = 6f
        val chartW = size.width - leftPad
        val chartH = size.height - bottomPad - topPad

        fun pointAt(index: Int, value: Int): Offset {
            val x = leftPad + chartW * (index.toFloat() / (totalPoints - 1))
            val y = topPad + chartH * (1f - value / maxV)
            return Offset(x, y)
        }

        // Baseline grid: 3 faint horizontal lines.
        for (g in 0..2) {
            val y = topPad + chartH * (g / 2f)
            drawLine(grid, Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1.5f)
        }

        val actualPoints = actual.mapIndexed { i, v -> pointAt(i, v) }
        val startIdx = actual.size - 1
        val projectedPoints = projected.mapIndexed { i, v -> pointAt(startIdx + i, v) }

        // Projected area fill under the dashed segment.
        if (projectedPoints.size >= 2) {
            val area = Path().apply {
                moveTo(projectedPoints.first().x, topPad + chartH)
                projectedPoints.forEach { lineTo(it.x, it.y) }
                lineTo(projectedPoints.last().x, topPad + chartH)
                close()
            }
            drawPath(
                area,
                brush = Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.0f)),
                ),
            )
        }

        // Solid actual line.
        val actualPath = Path().apply {
            actualPoints.forEachIndexed { i, p ->
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }
        drawPath(actualPath, color = accent, style = Stroke(width = 3.5f, cap = StrokeCap.Round))

        // Dashed projected line.
        val projPath = Path().apply {
            projectedPoints.forEachIndexed { i, p ->
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }
        drawPath(
            projPath,
            color = accent,
            style = Stroke(
                width = 3f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f)),
            ),
        )

        // Dots on actual points.
        actualPoints.forEach { p ->
            drawCircle(accent, radius = 5f, center = p)
            drawCircle(Color.White, radius = 2f, center = p)
        }
        // Hollow dot at the projected endpoint.
        projectedPoints.lastOrNull()?.let { p ->
            drawCircle(accent, radius = 5f, center = p)
            drawCircle(Color.White, radius = 2.5f, center = p)
        }
    }
}

// ── Risk gauge ──────────────────────────────────────────────────────

@Composable
private fun RiskGauge(percent: Int, modifier: Modifier = Modifier) {
    val fraction = (percent / 100f).coerceIn(0f, 1f)
    // Low risk = green, high risk = red; blend across the range.
    val riskColor = lerpColor(Color(0xFF00B894), Color(0xFFFF6B6B), fraction)
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 14f
            val diameter = min(size.width, size.height) - stroke
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f,
            )
            val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
            // 270° track starting at bottom-left, leaving a gap at the bottom.
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
                color = riskColor,
                startAngle = 135f,
                sweepAngle = 270f * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "risk",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Decay row ───────────────────────────────────────────────────────

@Composable
private fun DecayRow(item: DecayItem) {
    val riskColor = lerpColor(Color(0xFF00B894), Color(0xFFFF6B6B), item.risk)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.sign,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${item.daysSince} days since practice",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MeterTrack(
            fraction = item.risk,
            accent = riskColor,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        Text(
            text = "${(item.risk * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = riskColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── Helpers ─────────────────────────────────────────────────────────

@Composable
private fun LegendDot(color: Color, solid: Boolean) {
    if (solid) {
        Box(
            modifier = Modifier
                .size(width = 16.dp, height = 4.dp)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { i ->
                if (i > 0) Spacer(Modifier.size(3.dp))
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(color),
                )
            }
        }
    }
}

/** Simple linear blend between two colours (no theme dependency). */
private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * tt,
        green = a.green + (b.green - a.green) * tt,
        blue = a.blue + (b.blue - a.blue) * tt,
        alpha = 1f,
    )
}

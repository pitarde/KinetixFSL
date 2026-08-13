package com.example.kinetixfsl.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Tab 2 — Weak Spots (diagnostic analytics: "why it's happening").
 *
 * Confused sign pairs, per-module drop-off, and the error-type split.
 */
@Composable
fun WeakSpotsTab(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {

        // ── Confused pairs ──────────────────────────────────────────
        AnalyticsCard {
            CardHeader(
                icon = ProfileIcons.AlertTriangle,
                accent = AccentWeak,
                title = "Signs you mix up",
                subtitle = "How often each pair is confused",
            )
            Spacer(Modifier.height(14.dp))
            SampleProfile.confusionPairs.forEachIndexed { i, pair ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                MeterRow(
                    label = "${pair.first} vs ${pair.second}",
                    fraction = pair.confusedPercent / 100f,
                    valueText = "${pair.confusedPercent}%",
                    accent = AccentWeak,
                    labelWidth = 74.dp,
                )
            }
        }

        CardGap()

        // ── Drop-off per module ─────────────────────────────────────
        AnalyticsCard {
            CardHeader(
                icon = ProfileIcons.TrendingUp,
                accent = AccentWeak,
                title = "Where you drop off",
                subtitle = "Lessons started vs. completed",
            )
            Spacer(Modifier.height(14.dp))
            SampleProfile.dropOffs.forEachIndexed { i, d ->
                if (i > 0) Spacer(Modifier.height(14.dp))
                DropOffRow(d)
            }
            Spacer(Modifier.height(14.dp))
            LegendRow(
                items = listOf(
                    "Completed" to AccentWeak,
                    "Started" to MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                ),
            )
        }

        CardGap()

        // ── Error type breakdown ────────────────────────────────────
        AnalyticsCard {
            CardHeader(
                icon = ProfileIcons.Target,
                accent = AccentWeak,
                title = "What goes wrong",
                subtitle = "Your error types",
            )
            Spacer(Modifier.height(16.dp))
            ErrorSegmentedBar(SampleProfile.errorBreakdown)
        }
    }
}

// ── Drop-off row ────────────────────────────────────────────────────

@Composable
private fun DropOffRow(d: ModuleDropOff) {
    val completedFraction = if (d.started > 0) d.completed.toFloat() / d.started else 0f
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = d.module,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${d.completed}/${d.started}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        // Full track = started; coloured fill = completed portion.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(completedFraction)
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AccentWeak),
            )
        }
    }
}

// ── Error segmented bar ─────────────────────────────────────────────

@Composable
private fun ErrorSegmentedBar(e: ErrorBreakdown) {
    val handshape = Color(0xFFFF6B6B)
    val motion = Color(0xFFFFA94D)
    val timing = Color(0xFFFFD43B)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(50)),
        ) {
            Box(
                Modifier
                    .weight(e.handshape.coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(handshape),
            )
            Box(
                Modifier
                    .weight(e.motion.coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(motion),
            )
            Box(
                Modifier
                    .weight(e.timing.coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(timing),
            )
        }
        Spacer(Modifier.height(14.dp))
        ErrorLegendItem("Handshape", (e.handshape * 100).toInt(), handshape)
        Spacer(Modifier.height(8.dp))
        ErrorLegendItem("Motion", (e.motion * 100).toInt(), motion)
        Spacer(Modifier.height(8.dp))
        ErrorLegendItem("Timing", (e.timing * 100).toInt(), timing)
    }
}

@Composable
private fun ErrorLegendItem(label: String, percent: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── Small legend row (dots + labels) ────────────────────────────────

@Composable
private fun LegendRow(items: List<Pair<String, Color>>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { i, (label, color) ->
            if (i > 0) Spacer(Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

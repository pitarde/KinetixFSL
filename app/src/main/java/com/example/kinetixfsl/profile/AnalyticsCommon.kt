package com.example.kinetixfsl.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shared building blocks and the accent palette for the Profile analytics
 * section. The four accents are the ones specified for each tab; they read
 * clearly on both the white (light) and dark card surfaces, so they stay fixed
 * across themes while the surfaces and text colours come from [MaterialTheme].
 */

val AccentProgress = Color(0xFF6C5CE7) // Progress   — descriptive
val AccentWeak = Color(0xFFFF6B6B)     // Weak Spots — diagnostic
val AccentForecast = Color(0xFF00B894) // Forecast   — predictive
val AccentCoach = Color(0xFFA55EEA)    // Coach      — prescriptive

/** True when the active color scheme is a dark one, judged from the background. */
@Composable
fun isDarkScheme(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

/** The soft lavender page background in light mode; the theme background in dark. */
@Composable
fun profileBackground(): Color =
    if (isDarkScheme()) MaterialTheme.colorScheme.background else Color(0xFFEDEBFB)

/**
 * A white (or dark-surface) rounded card — the single container primitive the
 * whole analytics section is built from, so spacing and radius stay consistent.
 */
@Composable
fun AnalyticsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            // Border removed in light mode — used by Progress, Weak Spots,
            // Forecast, and Coach's Picks, all built from this one card.
            // Dark mode keeps it.
            .then(
                if (isSystemInDarkTheme()) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                } else {
                    Modifier
                },
            )
            .padding(16.dp),
        content = content,
    )
}

/** A small icon chip + title + optional subtitle, used at the top of each card. */
@Composable
fun CardHeader(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.size(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A labelled horizontal meter: `label` on the left, a rounded track with a
 * coloured fill, and a trailing value string. Used for mastery %, decay risk,
 * confusion %, etc.
 */
@Composable
fun MeterRow(
    label: String,
    fraction: Float,
    valueText: String,
    accent: Color,
    modifier: Modifier = Modifier,
    labelWidth: androidx.compose.ui.unit.Dp = 84.dp,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(labelWidth),
        )
        MeterTrack(
            fraction = fraction,
            accent = accent,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(42.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/** The bare rounded track + fill, reusable wherever a meter is embedded. */
@Composable
fun MeterTrack(
    fraction: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(accent),
        )
    }
}

/** Section spacer used between cards, kept in one place for rhythm. */
@Composable
fun CardGap() = Spacer(Modifier.height(14.dp))

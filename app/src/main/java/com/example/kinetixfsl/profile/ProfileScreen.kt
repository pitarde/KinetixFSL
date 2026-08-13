package com.example.kinetixfsl.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kinetixfsl.ui.home.DashboardViewModel
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme

/**
 * The Profile screen — user header + a four-tab analytics section.
 *
 * All numbers are sample data from [SampleProfile]; the layout is built so the
 * real progress/XP/ranking pipeline can be swapped in later without touching the
 * UI. Fully theme-aware: a soft lavender page in light mode, the dark surface
 * palette in dark mode, with white/dark cards from [MaterialTheme].
 */
@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(),
) {
    val name = viewModel.uiState.displayName
    val summary = remember { SampleProfile.summary }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(profileBackground())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        ProfileHeader(name = name, summary = summary, onSettings = { /* TODO settings */ })

        Spacer(Modifier.height(18.dp))

        StatGrid(summary)

        Spacer(Modifier.height(22.dp))

        AnalyticsTabRow(
            selected = selectedTab,
            onSelect = { selectedTab = it },
        )

        Spacer(Modifier.height(16.dp))

        when (selectedTab) {
            0 -> ProgressTab()
            1 -> WeakSpotsTab()
            2 -> ForecastTab()
            else -> CoachTab()
        }

        Spacer(Modifier.height(24.dp))

        SignOutButton(onSignOut)

        Spacer(Modifier.height(28.dp))
    }
}

// ── Header ──────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(
    name: String,
    summary: ProfileSummary,
    onSettings: () -> Unit,
) {
    val greeting = remember { greetingForNow() }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(name = name)
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$greeting,",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    RankPill(summary.rankTitle, summary.level)
                }
            }

            // Settings gear, top-right.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ProfileIcons.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Level progress
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Level ${summary.level}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${(summary.levelProgress * 100).toInt()}% to Level ${summary.level + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(8.dp))
            MeterTrack(
                fraction = summary.levelProgress,
                accent = AccentProgress,
                height = 10.dp,
            )
        }
    }
}

@Composable
private fun Avatar(name: String) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF6C5CE7))),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun RankPill(rankTitle: String, level: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(AccentProgress.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ProfileIcons.HandStar,
            contentDescription = null,
            tint = AccentProgress,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.size(5.dp))
        Text(
            text = rankTitle,
            style = MaterialTheme.typography.labelMedium,
            color = AccentProgress,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── Stat grid (2×2) ─────────────────────────────────────────────────

@Composable
private fun StatGrid(summary: ProfileSummary) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCard(
                icon = ProfileIcons.Flame,
                accent = AccentWeak,
                value = "${summary.streakDays}",
                label = "Day streak",
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(12.dp))
            StatCard(
                icon = ProfileIcons.HandStar,
                accent = AccentProgress,
                value = "${summary.signsLearned}",
                label = "Signs learned",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.size(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCard(
                icon = ProfileIcons.Clock,
                accent = AccentForecast,
                value = formatMinutes(summary.studyMinutes),
                label = "Study time",
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(12.dp))
            StatCard(
                icon = ProfileIcons.TrendingUp,
                accent = AccentCoach,
                value = "${summary.lessonsCompleted}",
                label = "Lessons done",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    accent: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Analytics tab row ───────────────────────────────────────────────

private data class TabSpec(val label: String, val icon: ImageVector, val accent: Color)

@Composable
private fun AnalyticsTabRow(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = remember {
        listOf(
            TabSpec("Progress", ProfileIcons.TrendingUp, AccentProgress),
            TabSpec("Weak Spots", ProfileIcons.AlertTriangle, AccentWeak),
            TabSpec("Forecast", ProfileIcons.Sparkles, AccentForecast),
            TabSpec("Coach's Picks", ProfileIcons.Wand, AccentCoach),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { i, spec ->
            TabChip(
                spec = spec,
                selected = i == selected,
                onClick = { onSelect(i) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TabChip(
    spec: TabSpec,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) spec.accent.copy(alpha = 0.16f) else Color.Transparent
    val fg = if (selected) spec.accent else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = spec.icon,
            contentDescription = spec.label,
            tint = fg,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = spec.label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
            color = fg,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Sign out ────────────────────────────────────────────────────────

@Composable
private fun SignOutButton(onSignOut: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onSignOut)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Sign out",
            style = MaterialTheme.typography.labelLarge,
            color = AccentWeak,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── Helpers ─────────────────────────────────────────────────────────

private fun greetingForNow(): String =
    when (java.time.LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }

private fun formatMinutes(minutes: Int): String {
    if (minutes < 60) return "${minutes}m"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "${h}h" else "${h}h ${m}m"
}

// ── Previews ────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true, name = "Profile – Light")
@Composable
private fun ProfileScreenPreviewLight() {
    KinetixFSLTheme(darkTheme = false) {
        ProfileScreen(onSignOut = {})
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Profile – Dark")
@Composable
private fun ProfileScreenPreviewDark() {
    KinetixFSLTheme(darkTheme = true) {
        ProfileScreen(onSignOut = {})
    }
}

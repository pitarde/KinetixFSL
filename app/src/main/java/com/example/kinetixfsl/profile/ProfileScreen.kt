package com.example.kinetixfsl.profile

import androidx.compose.foundation.Image
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kinetixfsl.progress.AchievementView
import com.example.kinetixfsl.progress.ProgressRepository
import com.example.kinetixfsl.progress.RankTier
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
    val context = LocalContext.current
    val name = viewModel.uiState.displayName

    // Real, per-account progress + analytics computed from local stores.
    val repo = remember { ProgressRepository(context) }
    val progress = remember { repo.snapshot() }
    val analytics = remember(progress) { computeAnalytics(repo, progress) }
    val summary = remember(progress, analytics) {
        ProfileSummary(
            rankTitle = progress.rank.title,
            level = progress.level,
            levelProgress = progress.levelProgress,
            streakDays = progress.streakDays,
            signsLearned = progress.signsLearned,
            studyMinutes = analytics.studyMinutes,
            lessonsCompleted = progress.quizLevelsCleared,
        )
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        AccountSettingsDialog(
            onDismiss = { showSettings = false },
            onSignOut = onSignOut,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(profileBackground())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        ProfileHeader(
            name = name,
            summary = summary,
            tier = progress.rank,
            onSettings = { showSettings = true },
        )

        Spacer(Modifier.height(18.dp))

        StatGrid(summary)

        Spacer(Modifier.height(18.dp))

        AchievementsGrid(progress.achievements)

        Spacer(Modifier.height(22.dp))

        AnalyticsTabRow(
            selected = selectedTab,
            onSelect = { selectedTab = it },
        )

        Spacer(Modifier.height(16.dp))

        when (selectedTab) {
            0 -> ProgressTab(data = analytics)
            1 -> WeakSpotsTab(data = analytics)
            2 -> ForecastTab(data = analytics)
            else -> CoachTab(data = analytics)
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
    tier: RankTier,
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
                    RankPill(tier = tier)
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
                modifier = Modifier.fillMaxWidth(),
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

/**
 * The rank badge under the username. Tapping it opens a dropdown listing all
 * four rank tiers the user can reach (Levels 1–5, 6–10, 11–15, 16–20), with the
 * current one highlighted.
 */
@Composable
private fun RankPill(tier: RankTier) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(AccentProgress.copy(alpha = 0.14f))
                .clickable { expanded = true }
                .padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(tier.badgeRes),
                contentDescription = "${tier.title} rank badge",
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.size(7.dp))
            Text(
                text = tier.title,
                style = MaterialTheme.typography.labelMedium,
                color = AccentProgress,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(4.dp))
            Text(text = "▾", color = AccentProgress, fontSize = 12.sp)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            RankTier.entries.forEach { rank ->
                val current = rank == tier
                DropdownMenuItem(
                    onClick = { expanded = false },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(rank.badgeRes),
                                contentDescription = null,
                                modifier = Modifier.size(34.dp),
                            )
                            Spacer(Modifier.size(12.dp))
                            Column {
                                Text(
                                    text = rank.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (current) AccentProgress
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Levels ${rank.levels.first}–${rank.levels.last}" +
                                        if (current) " · current" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

// ── Achievements grid ───────────────────────────────────────────────
//
// Placeholder circles for now (real badge artwork comes later). Unlock logic,
// storage, and XP live in the progress package; this just renders 10 slots and
// fills the ones that are unlocked.

@Composable
private fun AchievementsGrid(achievements: List<AchievementView>) {
    val unlocked = achievements.count { it.unlocked }
    // Tapping a badge opens a dialog explaining how to earn it.
    var selected by remember { mutableStateOf<AchievementView?>(null) }
    selected?.let { av ->
        com.example.kinetixfsl.ui.AchievementInfoDialog(view = av, onDismiss = { selected = null })
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Achievements",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$unlocked / ${achievements.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(14.dp))
        achievements.chunked(5).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { av ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        AchievementBadge(av, onClick = { selected = av })
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AchievementBadge(view: AchievementView, onClick: () -> Unit) {
    val bg = if (view.unlocked) AchievementGold
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (view.unlocked) view.achievement.emoji else "🔒",
                fontSize = 20.sp,
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = view.achievement.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

// Warm gold for unlocked achievement badges.
private val AchievementGold = Color(0xFFF4A62A)

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
    var confirming by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { confirming = true }
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

    if (confirming) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Sign out of your account?") },
            text = { Text("You'll need to sign back in to continue learning.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        confirming = false
                        onSignOut()
                    },
                ) { Text("Sign out", color = AccentWeak, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirming = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
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

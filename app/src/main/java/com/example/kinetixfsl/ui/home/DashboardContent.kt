package com.example.kinetixfsl.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme
import com.example.kinetixfsl.ui.theme.KinetixNavy
import com.example.kinetixfsl.ui.theme.KinetixWhite

/**
 * The Dashboard content — everything BELOW the top-bar hamburger and ABOVE the
 * bottom nav. Rendered inside [HomeScreen], which owns the drawer, top bar, and
 * bottom nav; this composable only draws the greeting, streak card, and module list.
 *
 * All lesson-card clicks are no-ops for now — real navigation comes when the
 * modules feature is built.
 */
@Composable
fun DashboardContent(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(),
    /** Opens a module's sign list (from a Continue-Learning card). */
    onOpenModule: (categoryId: String) -> Unit = {},
) {
    val state = viewModel.uiState

    // Real: streak + overall progress toward Level 20, rank badge, and the
    // in-progress modules the user has started but not finished.
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { com.example.kinetixfsl.progress.ProgressRepository(context) }
    val progress = remember { repo.snapshot() }
    val analytics = remember(progress) { com.example.kinetixfsl.profile.computeAnalytics(repo, progress) }
    val streak = remember(progress) {
        StreakSummary(
            streakDays = progress.streakDays,
            overallProgress = progress.accountXpForLevel /
                com.example.kinetixfsl.progress.XpEngine.LEVEL_CAP_XP.toFloat(),
        )
    }
    // Modules started but not finished, most-progressed first.
    val inProgress = remember(progress) {
        progress.categories
            .filter { it.learned in 1 until it.total }
            .sortedByDescending { it.learned.toFloat() / it.total }
            .map {
                ModuleProgress(
                    title = it.name,
                    subtitle = "${it.learned} of ${it.total} signs",
                    progress = it.learned.toFloat() / it.total,
                    xpLabel = "${it.learned}/${it.total}",
                    status = ModuleStatus.IN_PROGRESS,
                    categoryId = it.id,
                )
            }
    }
    var showAllModules by rememberSaveable { mutableStateOf(false) }
    val visibleModules = if (showAllModules) inProgress else inProgress.take(3)

    val achievements = progress.achievements
    var showAllBadges by rememberSaveable { mutableStateOf(false) }
    val visibleBadges = if (showAllBadges) achievements else achievements.take(5)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // ---- Greeting ----
        Text(
            text = "Good morning,",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = state.displayName,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(16.dp))
        StreakCard(
            streak = streak,
            rankBadgeRes = progress.rank.badgeRes,
            level = progress.level,
            levelProgress = progress.levelProgress,
        )

        Spacer(Modifier.height(14.dp))
        StatsCard(
            signsLearned = progress.signsLearned,
            weakSpots = analytics.confusionPairs.size,
            streakRisk = analytics.streakRiskPercent,
        )

        // ---- Continue learning ----
        Spacer(Modifier.height(18.dp))
        SectionHeader(
            title = "CONTINUE LEARNING",
            showToggle = inProgress.size > 3,
            expanded = showAllModules,
            onToggle = { showAllModules = !showAllModules },
        )
        Spacer(Modifier.height(10.dp))
        if (visibleModules.isEmpty()) {
            HintCard("No modules in progress yet — open the Modules tab to start one.")
        }
        visibleModules.forEach { module ->
            ModuleCard(module = module, onClick = { onOpenModule(module.categoryId) })
            Spacer(Modifier.height(10.dp))
        }

        // ---- Badges ----
        Spacer(Modifier.height(8.dp))
        SectionHeader(
            title = "BADGES",
            showToggle = achievements.size > 5,
            expanded = showAllBadges,
            onToggle = { showAllBadges = !showAllBadges },
        )
        Spacer(Modifier.height(12.dp))
        BadgesCard(visibleBadges)

        Spacer(Modifier.height(24.dp))
    }
}

/** A section title with an optional See all / Show less toggle on the right. */
@Composable
private fun SectionHeader(
    title: String,
    showToggle: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        if (showToggle) {
            Text(
                text = if (expanded) "Show less" else "See all",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Three at-a-glance stats in one card: signs learned, weak spots, streak risk. */
@Composable
private fun StatsCard(signsLearned: Int, weakSpots: Int, streakRisk: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatTile("$signsLearned", "Signs learned", Modifier.weight(1f))
        StatDivider()
        StatTile("$weakSpots", "Weak spots", Modifier.weight(1f))
        StatDivider()
        StatTile("$streakRisk%", "Streak risk", Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .height(34.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    )
}

/** The badges card — a wrap of achievement circles, filled when unlocked. */
@Composable
private fun BadgesCard(badges: List<com.example.kinetixfsl.progress.AchievementView>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp, horizontal = 8.dp),
    ) {
        badges.chunked(5).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { badge ->
                    BadgeChip(badge, Modifier.weight(1f))
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun BadgeChip(
    view: com.example.kinetixfsl.progress.AchievementView,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val bg = if (view.unlocked) GoldBadge
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun HintCard(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// A warm gold for unlocked badges (matches the reference).
private val GoldBadge = Color(0xFFF4A62A)

/**
 * The purple streak card. Big streak day count, subtle progress bar, and a
 * gold medal-star in the top-right.
 */
@Composable
private fun StreakCard(
    streak: StreakSummary,
    rankBadgeRes: Int,
    level: Int,
    levelProgress: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(KinetixNavy)
            .padding(20.dp),
    ) {
        Column {
            Text(
                text = "Current Streak",
                style = MaterialTheme.typography.bodyMedium,
                color = KinetixWhite.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${streak.streakDays} days",
                style = MaterialTheme.typography.displayLarge,
                color = KinetixWhite,
                fontWeight = FontWeight.ExtraBold,
            )

            Spacer(Modifier.height(16.dp))

            // Overall progress toward Level 20.
            NavyMeter(fraction = streak.overallProgress)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Overall progress ~ ${(streak.overallProgress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = KinetixWhite.copy(alpha = 0.75f),
            )

            Spacer(Modifier.height(14.dp))

            // Current-level progress, like the Profile screen.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Level $level",
                    style = MaterialTheme.typography.labelLarge,
                    color = KinetixWhite,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${(levelProgress * 100).toInt()}% to Level ${level + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = KinetixWhite.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.height(6.dp))
            NavyMeter(fraction = levelProgress)
        }

        // The user's current rank badge, top-right (derived from level).
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(46.dp)
                .clip(CircleShape)
                .background(KinetixWhite.copy(alpha = 0.15f))
                .padding(5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(rankBadgeRes),
                contentDescription = "Rank badge",
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

/** A pill meter for the navy streak card: white fill on a visible light track. */
@Composable
private fun NavyMeter(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(KinetixWhite.copy(alpha = 0.22f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(KinetixWhite),
        )
    }
}

/** One row in the "Continue Learning" list. */
@Composable
private fun ModuleCard(
    module: ModuleProgress,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = module.subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            StatusPill(status = module.status)
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { module.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = module.xpLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun StatusPill(status: ModuleStatus) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardContentPreview() {
    KinetixFSLTheme {
        DashboardContent()
    }
}
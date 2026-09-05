package com.example.kinetixfsl.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.LaunchedEffect
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
import com.example.kinetixfsl.profile.AccentProgress
import com.example.kinetixfsl.ui.theme.KinetixNavy
import com.example.kinetixfsl.ui.theme.KinetixPageBackground
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

    // A name set via Edit Profile (see LocalProfileStore) overrides the
    // account's own display name here too — read fresh on every composition
    // (this screen remounts each time the Home tab is shown) so a change
    // made on the Profile tab shows up the moment the user comes back here.
    // Shown exactly as typed — "Ken P." should read as "Ken P.", not get cut
    // down to "Ken" the way the original Firebase display name does.
    val displayName = com.example.kinetixfsl.profile.LocalProfileStore.getName(context)
        ?: state.displayName
    val repo = remember { com.example.kinetixfsl.progress.ProgressRepository(context) }
    // Mutable so claiming a streak day re-snapshots and the UI reflects the new
    // XP/level immediately.
    var progress by remember { mutableStateOf(repo.snapshot()) }
    // Opening the app counts as today's activity, so the login streak actually
    // advances day-to-day (it used to only move when you finished a lesson or
    // quiz, which is why it looked stuck at 1). Idempotent within a calendar day.
    // If a day (or more) was missed, the streak resets and this captures that so
    // the card can show a one-time "Skipped" notice instead of just silently
    // dropping back to Day 1.
    var streakSkipped by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val outcome = repo.recordPracticeDay()
        streakSkipped = outcome.isSkipped
        progress = repo.snapshot()
    }
    val analytics = remember(progress) { com.example.kinetixfsl.profile.computeAnalytics(repo, progress) }

    // Celebrate reaching a new rank tier. We persist the last-acknowledged rank
    // and pop the celebration once when the current rank climbs past it. The
    // default seeds to the current rank, so a brand-new user is never falsely
    // congratulated for merely starting at Novice.
    var rankUp by remember { mutableStateOf<com.example.kinetixfsl.progress.RankTier?>(null) }
    LaunchedEffect(progress.rank) {
        val prefs = context.getSharedPreferences("kinetix_rank", android.content.Context.MODE_PRIVATE)
        val acknowledged = prefs.getInt("ack_rank_ordinal", progress.rank.ordinal)
        if (progress.rank.ordinal > acknowledged) {
            rankUp = progress.rank
        }
        prefs.edit().putInt("ack_rank_ordinal", progress.rank.ordinal).apply()
    }
    rankUp?.let { tier ->
        com.example.kinetixfsl.ui.RankUpDialog(rank = tier, onDismiss = { rankUp = null })
    }

    DashboardScreenContent(
        displayName = displayName,
        progress = progress,
        weakSpots = analytics.confusionPairs.size,
        streakRisk = analytics.streakRiskPercent,
        streakSkipped = streakSkipped,
        modifier = modifier,
        onOpenModule = onOpenModule,
        onClaimStreak = { day ->
            val awarded = repo.claimStreakDay(day)
            if (awarded > 0) progress = repo.snapshot()
            awarded
        },
    )
}

/**
 * The actual dashboard layout, taking plain data instead of a ViewModel +
 * ProgressRepository. [DashboardContent] wires this from real sources; the
 * @Preview functions at the bottom of this file call it directly with fake
 * data, which is what makes a FULL-SCREEN preview possible — the ViewModel/
 * repository path needs a real Context (SharedPreferences) that the Android
 * Studio preview renderer can't provide.
 */
@Composable
private fun DashboardScreenContent(
    displayName: String,
    progress: com.example.kinetixfsl.progress.PlayerProgress,
    weakSpots: Int,
    streakRisk: Int,
    /** True when today's login followed a missed day — the streak card shows a
     *  one-time "Skipped" notice instead of silently resetting quietly. */
    streakSkipped: Boolean = false,
    modifier: Modifier = Modifier,
    onOpenModule: (categoryId: String) -> Unit = {},
    /** Claims streak [day]; returns XP awarded (0 if not claimable). */
    onClaimStreak: (day: Int) -> Int = { 0 },
) {
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
            // Off-white in light mode, matching the Home Feed, so the pure-
            // white cards below (colorScheme.surface already resolves to
            // that) actually read as cards instead of blending into the
            // page. Dark mode is untouched.
            .background(if (isSystemInDarkTheme()) MaterialTheme.colorScheme.background else KinetixPageBackground)
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
            text = displayName,
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
            claimedDays = progress.claimedStreakDays,
            skipped = streakSkipped,
            onClaimStreak = onClaimStreak,
        )

        Spacer(Modifier.height(14.dp))
        StatsCard(
            signsLearned = progress.signsLearned,
            weakSpots = weakSpots,
            streakRisk = streakRisk,
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
    // Tapping any badge explains how to earn it.
    var selected by remember {
        mutableStateOf<com.example.kinetixfsl.progress.AchievementView?>(null)
    }
    selected?.let { av ->
        com.example.kinetixfsl.ui.AchievementInfoDialog(view = av, onDismiss = { selected = null })
    }
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
                    BadgeChip(badge, Modifier.weight(1f), onClick = { selected = badge })
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
    onClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
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
    claimedDays: Set<Int> = emptySet(),
    /** Today's login followed a missed day — show a one-time "Skipped" notice. */
    skipped: Boolean = false,
    onClaimStreak: (day: Int) -> Int = { 0 },
) {
    // Tapping the card opens the daily-streak reward sheet.
    var showClaim by remember { mutableStateOf(false) }
    if (showClaim) {
        com.example.kinetixfsl.ui.StreakClaimDialog(
            streakDays = streak.streakDays,
            claimedDays = claimedDays,
            onClaim = onClaimStreak,
            onDismiss = { showClaim = false },
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            // KinetixNavy in light mode. Dark mode uses colorScheme.surface —
            // the same color the community feed's post cards use for their
            // dark-mode background — instead of the fixed navy, which read
            // barely different from the page behind it there.
            .background(if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else KinetixNavy)
            .clickable { showClaim = true }
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
            // One-time notice: a day (or more) was missed since the last login,
            // so the streak reset and any days not already claimed are gone —
            // this session's login started the count back at 1.
            if (skipped) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(KinetixWhite.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "⚠️", fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Skipped — streak reset to Day 1",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = KinetixWhite,
                    )
                }
            }

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
                // Profile-screen progress accent, used for every bar app-wide.
                .background(AccentProgress),
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
                color = AccentProgress,
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

// ── Previews ────────────────────────────────────────────────────
//
// DashboardContent() itself pulls a real ViewModel + ProgressRepository
// (SharedPreferences via LocalContext), which the Preview renderer often
// can't satisfy. DashboardScreenContent() is the same layout with that
// dependency removed — plain data in, so it previews FULL-SCREEN with fake
// data below, exactly like RegisterScreenPreview does for RegisterContent.

private val PreviewProgress = com.example.kinetixfsl.progress.PlayerProgress(
    accountXpRaw = 4200,
    level = 7,
    levelProgress = 0.55f,
    xpIntoLevel = 320,
    maxObtainable = 9500,
    rank = com.example.kinetixfsl.progress.RankTier.SKILLED,
    streakDays = 5,
    streakMilestones = 1,
    signsLearned = 42,
    quizLevelsCleared = 3,
    categoryXp = 1200,
    quizXp = 600,
    streakXp = 150,
    achievementXp = 300,
    categories = listOf(
        com.example.kinetixfsl.progress.CategoryMasteryInfo(
            id = "alphabet", name = "Alphabet", xp = 500,
            fraction = 0.64f, learned = 18, total = 28,
        ),
        com.example.kinetixfsl.progress.CategoryMasteryInfo(
            id = "numbers", name = "Numbers", xp = 200,
            fraction = 0.40f, learned = 4, total = 10,
        ),
        com.example.kinetixfsl.progress.CategoryMasteryInfo(
            id = "greetings", name = "Greetings", xp = 150,
            fraction = 0.75f, learned = 3, total = 4,
        ),
    ),
    achievements = com.example.kinetixfsl.progress.Achievement.entries
        .mapIndexed { i, a -> com.example.kinetixfsl.progress.AchievementView(a, unlocked = i % 2 == 0) },
)

@Preview(showBackground = true, showSystemUi = true, name = "Dashboard - Light")
@Composable
private fun DashboardScreenPreviewLight() {
    KinetixFSLTheme(darkTheme = false) {
        DashboardScreenContent(
            displayName = "Ken",
            progress = PreviewProgress,
            weakSpots = 3,
            streakRisk = 20,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Dashboard - Dark")
@Composable
private fun DashboardScreenPreviewDark() {
    KinetixFSLTheme(darkTheme = true) {
        DashboardScreenContent(
            displayName = "Ken",
            progress = PreviewProgress,
            weakSpots = 3,
            streakRisk = 20,
        )
    }
}

private val PreviewBadges = com.example.kinetixfsl.progress.Achievement.entries
    .mapIndexed { i, a -> com.example.kinetixfsl.progress.AchievementView(a, unlocked = i % 2 == 0) }
    .take(5)

private val PreviewModule = ModuleProgress(
    title = "Alphabet",
    subtitle = "18 of 28 signs",
    progress = 0.64f,
    xpLabel = "18/28",
    status = ModuleStatus.IN_PROGRESS,
)

@Preview(showBackground = true, showSystemUi = true, name = "Streak card - Light")
@Composable
private fun StreakCardPreviewLight() {
    KinetixFSLTheme(darkTheme = false) {
        StreakCard(
            streak = StreakSummary(streakDays = 5, overallProgress = 0.42f),
            rankBadgeRes = com.example.kinetixfsl.progress.RankTier.SKILLED.badgeRes,
            level = 7,
            levelProgress = 0.55f,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Streak card - Dark")
@Composable
private fun StreakCardPreviewDark() {
    KinetixFSLTheme(darkTheme = true) {
        StreakCard(
            streak = StreakSummary(streakDays = 5, overallProgress = 0.42f),
            rankBadgeRes = com.example.kinetixfsl.progress.RankTier.SKILLED.badgeRes,
            level = 7,
            levelProgress = 0.55f,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Stats card - Light")
@Composable
private fun StatsCardPreviewLight() {
    KinetixFSLTheme(darkTheme = false) {
        StatsCard(signsLearned = 42, weakSpots = 3, streakRisk = 20)
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Stats card - Dark")
@Composable
private fun StatsCardPreviewDark() {
    KinetixFSLTheme(darkTheme = true) {
        StatsCard(signsLearned = 42, weakSpots = 3, streakRisk = 20)
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Module card - Light")
@Composable
private fun ModuleCardPreviewLight() {
    KinetixFSLTheme(darkTheme = false) {
        ModuleCard(module = PreviewModule, onClick = {})
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Module card - Dark")
@Composable
private fun ModuleCardPreviewDark() {
    KinetixFSLTheme(darkTheme = true) {
        ModuleCard(module = PreviewModule, onClick = {})
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Badges card - Light")
@Composable
private fun BadgesCardPreviewLight() {
    KinetixFSLTheme(darkTheme = false) {
        BadgesCard(PreviewBadges)
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Badges card - Dark")
@Composable
private fun BadgesCardPreviewDark() {
    KinetixFSLTheme(darkTheme = true) {
        BadgesCard(PreviewBadges)
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Hint card - Light")
@Composable
private fun HintCardPreviewLight() {
    KinetixFSLTheme(darkTheme = false) {
        HintCard("No modules in progress yet — open the Modules tab to start one.")
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Hint card - Dark")
@Composable
private fun HintCardPreviewDark() {
    KinetixFSLTheme(darkTheme = true) {
        HintCard("No modules in progress yet — open the Modules tab to start one.")
    }
}
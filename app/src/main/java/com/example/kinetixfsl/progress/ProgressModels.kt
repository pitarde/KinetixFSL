package com.example.kinetixfsl.progress

import androidx.annotation.DrawableRes
import com.example.kinetixfsl.R

/**
 * The four rank tiers, mapped to Account-Level ranges, each with its badge
 * drawable (converted from the SVGs in Documents/Rank Badges). Derive the tier
 * from the current level on every read — never hardcode.
 */
enum class RankTier(
    val levels: IntRange,
    val title: String,
    @DrawableRes val badgeRes: Int,
) {
    NOVICE(1..5, "Novice Signer", R.drawable.rank_1_5),
    SKILLED(6..10, "Skilled Signer", R.drawable.rank_6_10),
    EXPERT(11..15, "Expert Signer", R.drawable.rank_11_15),
    MASTER(16..20, "Master Signer", R.drawable.rank_16_20);

    companion object {
        fun forLevel(level: Int): RankTier =
            entries.firstOrNull { level in it.levels } ?: NOVICE
    }
}

/**
 * The 10 achievement badges. Metadata only — the unlock rules live in
 * [ProgressRepository] because they read across all progress sources. Each
 * unlock is worth a one-time 300 XP. Artwork is a placeholder circle for now.
 */
enum class Achievement(val title: String, val detail: String, val emoji: String) {
    FIRST_LESSON("First Steps", "Complete your first lesson", "👣"),
    FIRST_QUIZ("Quiz Rookie", "Complete your first quiz level", "📝"),
    PERFECT_QUIZ("Flawless", "Score a perfect 5/5 on any quiz level", "⭐"),
    MASTER_ONE("Category Master", "Master your first full category", "🥉"),
    MASTER_THREE("Triple Threat", "Master 3 full categories", "🥈"),
    MASTER_ALL("Completionist", "Master all 7 categories", "👑"),
    CLEAR_ALL_QUIZ("Quiz Champion", "Clear all 10 quiz levels", "🏆"),
    STREAK_3("On a Roll", "Reach a 3-day streak", "🔥"),
    LEVEL_10("Halfway Hero", "Reach Account Level 10", "🛡️"),
    COMEBACK("Welcome Back", "Return after a break of 3+ days", "🔄");
}

/** Per-category mastery for the Progress tab and completion checks. */
data class CategoryMasteryInfo(
    val id: String,
    val name: String,
    val xp: Int,
    val fraction: Float,
    val learned: Int,
    val total: Int,
) {
    val complete: Boolean get() = total > 0 && learned >= total
}

/** UI view of one achievement: its metadata plus whether it's unlocked. */
data class AchievementView(
    val achievement: Achievement,
    val unlocked: Boolean,
)

/**
 * What happened to the login streak on one [ProgressRepository.recordPracticeDay]
 * call — lets a caller distinguish "kept going", "brand new", "same day again"
 * (no-op) from "a day was missed and the streak reset", so the UI can show a
 * "Skipped" notice instead of silently starting the count back over.
 */
data class StreakOutcome(
    val kind: Kind,
    /** Full calendar days missed between the last login and today (SKIPPED only). */
    val skippedDays: Int = 0,
) {
    enum class Kind { STARTED, CONTINUED, SKIPPED, SAME_DAY }
    val isSkipped: Boolean get() = kind == Kind.SKIPPED
}

/**
 * An immutable snapshot of everything the Profile/Dashboard need. Computed by
 * [ProgressRepository] from the local stores via [XpEngine].
 */
data class PlayerProgress(
    val accountXpRaw: Int,
    val level: Int,
    val levelProgress: Float,
    val xpIntoLevel: Int,
    val maxObtainable: Int,
    val rank: RankTier,
    val streakDays: Int,
    val streakMilestones: Int,
    /** Streak days (1..6) already claimed for XP — drives the claim UI. */
    val claimedStreakDays: Set<Int> = emptySet(),
    val signsLearned: Int,
    val quizLevelsCleared: Int,
    val categoryXp: Int,
    val quizXp: Int,
    val streakXp: Int,
    val achievementXp: Int,
    val categories: List<CategoryMasteryInfo>,
    val achievements: List<AchievementView>,
) {
    /** XP that actually counts toward Level (clamped at the 9,500 cap). */
    val accountXpForLevel: Int get() = accountXpRaw.coerceAtMost(XpEngine.LEVEL_CAP_XP)
    val atMaxLevel: Boolean get() = level >= XpEngine.MAX_LEVEL
    val unlockedAchievementCount: Int get() = achievements.count { it.unlocked }
}

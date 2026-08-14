package com.example.kinetixfsl.progress

/**
 * Pure XP & leveling math — no Android, no storage — implementing the XP spec
 * exactly. Every rule here is deterministic and unit-testable.
 *
 * Totals by design (max obtainable = 12,500; only 9,500 needed for Level 20):
 *   - 7 categories × 500        = 3,500
 *   - 10 quiz levels × 300      = 3,000  (first clear only)
 *   - 6 streak milestones × 500 = 3,000
 *   - 10 achievements × 300     = 3,000
 *
 * Level is a hard cap: once cumulative Account XP reaches 9,500, Level locks at
 * 20 forever. Raw XP beyond 9,500 is still tracked (for "9,500 / 12,500
 * collected") but never raises Level.
 */
object XpEngine {

    // Leveling
    const val XP_PER_LEVEL = 500
    const val MAX_LEVEL = 20
    /** Cumulative XP that locks Level at 20: 19 level-ups × 500. */
    const val LEVEL_CAP_XP = (MAX_LEVEL - 1) * XP_PER_LEVEL // 9,500

    // Categories
    const val CATEGORY_ITEM_POOL = 400
    const val CATEGORY_COMPLETION_BONUS = 100
    const val CATEGORY_MAX = 500

    // Quiz
    const val QUIZ_CORRECT_XP = 50
    const val QUIZ_COMPLETION_BONUS = 50
    const val QUIZ_QUESTIONS = 5
    const val QUIZ_LEVEL_MAX = 300

    // Streak
    const val STREAK_MILESTONE_XP = 500
    const val STREAK_MAX_MILESTONES = 6

    // Achievements
    const val ACHIEVEMENT_XP = 300
    const val ACHIEVEMENT_COUNT = 10

    /** Grand total of every source, for the "collected" display. */
    const val MAX_OBTAINABLE_XP =
        7 * CATEGORY_MAX + 10 * QUIZ_LEVEL_MAX +
            STREAK_MAX_MILESTONES * STREAK_MILESTONE_XP + ACHIEVEMENT_COUNT * ACHIEVEMENT_XP // 12,500

    // ── Category XP ────────────────────────────────────────────────────────

    /**
     * The per-item XP split for a category of [itemCount] items: an even
     * `400 ÷ count`, with any rounding remainder placed on the **last** item so
     * the list sums to exactly 400.
     */
    fun perItemXp(itemCount: Int): List<Int> {
        if (itemCount <= 0) return emptyList()
        val base = CATEGORY_ITEM_POOL / itemCount
        val remainder = CATEGORY_ITEM_POOL - base * itemCount
        return List(itemCount) { i -> if (i == itemCount - 1) base + remainder else base }
    }

    /**
     * XP earned in one category. [orderedItemIds] is the category's items in a
     * stable order (so the remainder always lands on the same last item);
     * [learned] is the set of item ids the user has learned. Completing every
     * item adds the flat +100 bonus. Capped at 500.
     */
    fun categoryXp(orderedItemIds: List<String>, learned: Set<String>): Int {
        if (orderedItemIds.isEmpty()) return 0
        val per = perItemXp(orderedItemIds.size)
        var xp = 0
        orderedItemIds.forEachIndexed { i, id -> if (id in learned) xp += per[i] }
        if (orderedItemIds.all { it in learned }) xp += CATEGORY_COMPLETION_BONUS
        return xp.coerceAtMost(CATEGORY_MAX)
    }

    /** A category's mastery fraction (0f..1f) for the Progress tab. */
    fun categoryMastery(orderedItemIds: List<String>, learned: Set<String>): Float =
        categoryXp(orderedItemIds, learned) / CATEGORY_MAX.toFloat()

    // ── Quiz XP ────────────────────────────────────────────────────────────

    /** XP for a single quiz level's first clear: 50 per correct + 50 completion, max 300. */
    fun quizLevelXp(correctCount: Int): Int =
        (correctCount.coerceIn(0, QUIZ_QUESTIONS) * QUIZ_CORRECT_XP + QUIZ_COMPLETION_BONUS)
            .coerceAtMost(QUIZ_LEVEL_MAX)

    /** Total quiz XP from the first-clear score of each cleared level. */
    fun quizXp(firstClearScores: Map<Int, Int>): Int =
        firstClearScores.values.sumOf { quizLevelXp(it) }

    // ── Streak & achievements ──────────────────────────────────────────────

    fun streakXp(milestones: Int): Int =
        milestones.coerceIn(0, STREAK_MAX_MILESTONES) * STREAK_MILESTONE_XP

    fun achievementXp(unlockedCount: Int): Int =
        unlockedCount.coerceIn(0, ACHIEVEMENT_COUNT) * ACHIEVEMENT_XP

    // ── Level ──────────────────────────────────────────────────────────────

    /** Level for a given raw Account XP, clamped to [MAX_LEVEL]. */
    fun levelForXp(accountXpRaw: Int): Int {
        val capped = accountXpRaw.coerceIn(0, LEVEL_CAP_XP)
        return (1 + capped / XP_PER_LEVEL).coerceAtMost(MAX_LEVEL)
    }

    /** XP accumulated within the current level (0..500); full 500 at max level. */
    fun xpIntoLevel(accountXpRaw: Int): Int {
        if (levelForXp(accountXpRaw) >= MAX_LEVEL) return XP_PER_LEVEL
        return accountXpRaw.coerceIn(0, LEVEL_CAP_XP) % XP_PER_LEVEL
    }

    /** Progress toward the next level, 0f..1f (1f at max level). */
    fun levelProgress(accountXpRaw: Int): Float =
        if (levelForXp(accountXpRaw) >= MAX_LEVEL) 1f
        else xpIntoLevel(accountXpRaw) / XP_PER_LEVEL.toFloat()
}

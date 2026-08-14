package com.example.kinetixfsl.game.data

import android.content.Context
import android.util.Log
import com.example.kinetixfsl.game.model.LevelPhase
import com.example.kinetixfsl.game.model.LevelPlan
import com.example.kinetixfsl.game.model.LevelStatus
import com.example.kinetixfsl.game.model.MAX_LEVEL
import com.example.kinetixfsl.game.model.Tier

/**
 * The single owner of quiz progress and content selection. The ViewModel talks
 * only to this; it in turn reads/writes [QuizStore] and pulls banks from
 * [QuizContent]. All progress mutations persist immediately so the game survives
 * an app kill at any point.
 */
class QuizRepository(context: Context) {

    private val store = QuizStore(context)
    private var progress: QuizProgress = store.load()

    // ── read ──────────────────────────────────────────────────────────────

    fun statusOf(level: Int): LevelStatus = when {
        level in progress.passedLevels -> LevelStatus.PASSED
        level <= progress.unlockedMaxLevel -> LevelStatus.UNLOCKED
        else -> LevelStatus.LOCKED
    }

    /** The lowest not-yet-passed unlocked level — the one the map highlights. */
    fun currentLevel(): Int =
        (1..MAX_LEVEL).firstOrNull { statusOf(it) == LevelStatus.UNLOCKED }
            ?: progress.unlockedMaxLevel

    fun passedCount(): Int = progress.passedLevels.count()

    /** An unfinished attempt to offer to resume, or null. */
    fun pendingSession(): QuizSession? = progress.session

    // ── start / resume a level ───────────────────────────────────────────────

    /**
     * Produces the session to play for [level]. Calls selection (A) the very
     * first time a level is assigned, else (B) for a deliberate replay. The new
     * session is persisted as the active one before returning.
     */
    fun startLevel(level: Int): QuizSession {
        val tier = Tier.forLevel(level)
        val bank = QuizContent.bank(tier)

        val plan: LevelPlan
        if (level !in progress.firstAssignedLevels) {
            // (A) initial tier-unlock pass — no repeats across the tier's levels.
            val used = progress.usedInitialPass[tier].orEmpty().toMutableSet()
            val signs = QuizGenerator.assignFirstTimeSigns(bank, used)
            plan = QuizGenerator.buildPlan(level, signs, bank)
            progress = progress.copy(
                firstAssignedLevels = progress.firstAssignedLevels + level,
                usedInitialPass = progress.usedInitialPass + (tier to used),
            )
        } else {
            // (B) voluntary replay — fresh random five from the whole tier pool.
            val signs = QuizGenerator.assignReplaySigns(bank)
            plan = QuizGenerator.buildPlan(level, signs, bank)
        }

        val session = QuizSession(
            plan = plan,
            phase = LevelPhase.VIDEO,
            index = 0,
            answers = List(plan.questions.size) { null },
        )
        persistSession(session)
        return session
    }

    /** Persist progress through a level (phase step, answer recorded, etc.). */
    fun persistSession(session: QuizSession) {
        progress = progress.copy(session = session)
        store.save(progress)
    }

    /** Drop the active session without changing unlocks (e.g. user tapped "No"). */
    fun clearSession() {
        if (progress.session == null) return
        progress = progress.copy(session = null)
        store.save(progress)
    }

    // ── finish a level ────────────────────────────────────────────────────────

    /**
     * Records the outcome of a completed attempt. On pass, marks the level passed
     * and unlocks the next one (sequential). Always clears the active session.
     * Fires the XP hook regardless of the (not-yet-designed) scoring.
     */
    fun completeLevel(level: Int, correctCount: Int, passed: Boolean) {
        var next = progress.copy(session = null)
        if (passed) {
            next = next.copy(
                passedLevels = next.passedLevels + level,
                unlockedMaxLevel = maxOf(next.unlockedMaxLevel, (level + 1).coerceAtMost(MAX_LEVEL)),
                // Record the score of the FIRST clear only — later replays don't
                // overwrite it, keeping quiz XP a one-time award per level.
                firstClearScores = if (level in next.firstClearScores) next.firstClearScores
                else next.firstClearScores + (level to correctCount),
            )
        }
        progress = next
        store.save(progress)
        onLevelPassed(level, correctCount, passed)
    }

    /**
     * XP placeholder hook (§8). Values/scaling are a follow-up spec; for now this
     * just records the event. Wire real XP + Firestore sync here later.
     */
    private fun onLevelPassed(level: Int, correctCount: Int, passed: Boolean, xpAwarded: Int = XP_TBD) {
        Log.d(TAG, "onLevelPassed(level=$level, correct=$correctCount, passed=$passed, xp=$xpAwarded)")
        // TODO: award XP and enqueue a background Firestore sync of unlocks/passes.
    }

    private companion object {
        const val TAG = "QuizRepository"
        const val XP_TBD = 0
    }
}

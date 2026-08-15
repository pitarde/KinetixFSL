package com.example.kinetixfsl.game

import android.content.Context
import com.example.kinetixfsl.game.data.QuizRepository
import com.example.kinetixfsl.game.data.QuizSession
import com.example.kinetixfsl.game.data.QuizGenerator
import com.example.kinetixfsl.game.model.LevelPhase
import com.example.kinetixfsl.game.model.LevelPlan
import com.example.kinetixfsl.game.model.LevelStatus
import com.example.kinetixfsl.game.model.MAX_LEVEL
import com.example.kinetixfsl.game.model.levelTitle
import com.example.kinetixfsl.game.model.PASS_THRESHOLD
import com.example.kinetixfsl.progress.XpEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One node on the level roadmap. */
data class LevelCard(
    val level: Int,
    val status: LevelStatus,
    val title: String,
    /** XP already earned from this level's first clear (0 until it's passed). */
    val xpEarned: Int,
    /** Max XP this level can award on a first clear (a perfect 5/5). */
    val xpReward: Int,
)

/** What the whole Quiz Game tab is showing right now. */
sealed interface QuizScreen {
    data object Loading : QuizScreen

    data class Map(
        val levels: List<LevelCard>,
        /** The level the "CURRENT GOAL" card points at (a resume target if any). */
        val goalLevel: Int,
        val goalTitle: String,
        /** Total quiz XP earned so far across every cleared level. */
        val goalXp: Int,
        /** Total quiz XP obtainable from all levels (10 × 300 = 3,000). */
        val xpGoal: Int,
        /** True when an unfinished attempt can be resumed. */
        val canResume: Boolean,
        val passedCount: Int,
        /** Populated only to drive the auto "continue last session?" popup. */
        val resumeSession: QuizSession?,
    ) : QuizScreen {
        val currentLevel: Int get() = goalLevel
    }

    data class Tutorial(val plan: LevelPlan, val index: Int) : QuizScreen

    data class Quiz(val plan: LevelPlan, val index: Int, val answers: List<Boolean?>) : QuizScreen

    data class Result(
        val plan: LevelPlan,
        val correctCount: Int,
        val passed: Boolean,
        val elapsedSeconds: Int,
        /** XP awarded for this attempt (0 on a fail or a replay of a cleared level). */
        val xpEarned: Int,
        /** Total quiz XP before this attempt — the progress bar animates from here. */
        val quizXpBefore: Int = 0,
        /** Total quiz XP obtainable (10 × 300 = 3,000), the bar's full width. */
        val quizXpGoal: Int = 1,
        /** Achievements unlocked by finishing this attempt (for the unlock animation). */
        val newAchievements: List<com.example.kinetixfsl.progress.Achievement> = emptyList(),
    ) : QuizScreen
}

/**
 * Drives the Quiz Game. A plain class (matching the app's `remember { … }`
 * view-model style) — every operation is a synchronous read/write against
 * [QuizRepository], so no coroutine scope is needed.
 */
class QuizGameViewModel(context: Context) {

    private val repo = QuizRepository(context)
    private val progress = com.example.kinetixfsl.progress.ProgressRepository(context)

    /** Wall-clock start of the current quiz phase, for the result screen's timer. */
    private var quizStartMillis = 0L

    private val _screen = MutableStateFlow<QuizScreen>(QuizScreen.Loading)
    val screen: StateFlow<QuizScreen> = _screen.asStateFlow()

    /** True whenever a level attempt is on screen — the host hides the bottom nav. */
    val isImmersive: Boolean
        get() = when (_screen.value) {
            is QuizScreen.Tutorial, is QuizScreen.Quiz, is QuizScreen.Result -> true
            else -> false
        }

    init {
        // Only auto-offer resume the first time the game opens this app session —
        // after that the goal card's RESUME button is the way back in, so the
        // popup isn't shown again.
        showMap(offerResume = !resumePromptConsumed)
    }

    // ── level map ─────────────────────────────────────────────────────────

    private fun showMap(offerResume: Boolean) {
        val pending = repo.pendingSession()
        if (offerResume && pending != null) resumePromptConsumed = true
        val current = repo.currentLevel()
        // The goal card points at the resume target if there is one, else the
        // current level.
        val goalLevel = pending?.plan?.level ?: current

        // Real quiz XP: the goal bar tracks total XP earned across all cleared
        // levels toward the game's full 3,000-XP quiz pool, so it actually moves
        // as levels are passed (the old "steps × 10" was 0 unless mid-attempt).
        val bestScores = repo.bestScores()
        val quizXpEarned = XpEngine.quizXp(bestScores)
        val quizXpTotal = MAX_LEVEL * XpEngine.QUIZ_LEVEL_MAX

        _screen.value = QuizScreen.Map(
            levels = (1..MAX_LEVEL).map { level ->
                LevelCard(
                    level = level,
                    status = repo.statusOf(level),
                    title = levelTitle(level),
                    // XP banked so far for this level = 60 × best score.
                    xpEarned = XpEngine.quizLevelXp(bestScores[level] ?: 0),
                    xpReward = XpEngine.QUIZ_LEVEL_MAX,
                )
            },
            goalLevel = goalLevel,
            goalTitle = levelTitle(goalLevel),
            goalXp = quizXpEarned,
            xpGoal = quizXpTotal,
            canResume = pending != null,
            passedCount = repo.passedCount(),
            resumeSession = if (offerResume) pending else null,
        )
    }

    /** The goal card's button: resume the unfinished attempt, or start the level. */
    fun onGoalAction() {
        val map = _screen.value as? QuizScreen.Map ?: return
        if (map.canResume) resumeLast() else startLevel(map.goalLevel)
    }

    fun dismissResumePrompt() {
        val map = _screen.value as? QuizScreen.Map ?: return
        _screen.value = map.copy(resumeSession = null)
    }

    fun startLevel(level: Int) {
        if (repo.statusOf(level) == LevelStatus.LOCKED) return
        enter(repo.startLevel(level))
    }

    fun resumeLast() {
        val session = repo.pendingSession() ?: run { showMap(offerResume = false); return }
        enter(session)
    }

    /** Route a session to the right screen based on its persisted phase. */
    private fun enter(session: QuizSession) {
        _screen.value = when (session.phase) {
            LevelPhase.VIDEO -> QuizScreen.Tutorial(session.plan, session.index)
            LevelPhase.QUIZ -> {
                quizStartMillis = System.currentTimeMillis()
                QuizScreen.Quiz(session.plan, session.index, session.answers)
            }
        }
    }

    /** X button on tutorial/quiz — leave the attempt intact so it can be resumed. */
    fun exitToMap() = showMap(offerResume = false)

    // ── tutorial phase ───────────────────────────────────────────────────────

    fun tutorialNext() {
        val s = _screen.value as? QuizScreen.Tutorial ?: return
        val next = s.index + 1
        if (next < s.plan.signIds.size) {
            repo.persistSession(QuizSession(s.plan, LevelPhase.VIDEO, next, freshAnswers(s.plan)))
            _screen.value = s.copy(index = next)
        } else {
            // Tutorial done → start the quiz phase at question 0.
            val answers = freshAnswers(s.plan)
            repo.persistSession(QuizSession(s.plan, LevelPhase.QUIZ, 0, answers))
            quizStartMillis = System.currentTimeMillis()
            _screen.value = QuizScreen.Quiz(s.plan, 0, answers)
        }
    }

    /** The prev button — step back to the previous sign in the tutorial. */
    fun tutorialPrev() {
        val s = _screen.value as? QuizScreen.Tutorial ?: return
        if (s.index == 0) return
        val prev = s.index - 1
        repo.persistSession(QuizSession(s.plan, LevelPhase.VIDEO, prev, freshAnswers(s.plan)))
        _screen.value = s.copy(index = prev)
    }

    // ── quiz phase ────────────────────────────────────────────────────────────

    /** Log a wrong answer as a confusion pair for the Weak Spots analytics. */
    fun recordMistake(targetSignId: String, targetWord: String, chosenWord: String) {
        progress.recordQuizMistake(targetSignId, targetWord, chosenWord)
    }

    /** Record the result of the current question (only the first submission counts). */
    fun submitAnswer(correct: Boolean) {
        val s = _screen.value as? QuizScreen.Quiz ?: return
        if (s.answers.getOrNull(s.index) != null) return
        // Log accuracy by hour + category for the best-time and adaptive analytics.
        val question = s.plan.questions[s.index]
        val categoryId = com.example.kinetixfsl.game.data.QuizContent
            .sign(question.targetSignId)?.categoryId ?: ""
        progress.recordQuizAnswer(categoryId, correct)
        val answers = s.answers.toMutableList().also { it[s.index] = correct }
        repo.persistSession(QuizSession(s.plan, LevelPhase.QUIZ, s.index, answers))
        _screen.value = s.copy(answers = answers)
    }

    fun quizNext() {
        val s = _screen.value as? QuizScreen.Quiz ?: return
        val next = s.index + 1
        if (next < s.plan.questions.size) {
            repo.persistSession(QuizSession(s.plan, LevelPhase.QUIZ, next, s.answers))
            _screen.value = s.copy(index = next)
        } else {
            val correct = s.answers.count { it == true }
            val passed = correct >= PASS_THRESHOLD
            val elapsed = if (quizStartMillis == 0L) 0
                else ((System.currentTimeMillis() - quizStartMillis) / 1000).toInt()
            // XP tracks the BEST score per level: an attempt only earns the
            // difference between this run's correct count and the best banked so
            // far (60 per extra correct). Beating your best on a retake tops it up;
            // matching or doing worse earns nothing.
            val previousBest = repo.bestScores()[s.plan.level] ?: 0
            val newBest = maxOf(previousBest, correct)
            val xpEarned = (newBest - previousBest) * XpEngine.QUIZ_XP_PER_CORRECT
            // Snapshot quiz XP + unlocked achievements BEFORE the clear is recorded
            // so the results screen can animate the +XP into the bar and celebrate
            // anything this attempt newly unlocked.
            val quizXpBefore = XpEngine.quizXp(repo.bestScores())
            val achievementsBefore = progress.unlockedAchievements()
            repo.completeLevel(s.plan.level, correct, passed)
            // Finishing a quiz counts as a practice day (streak), logs study time,
            // and may unlock achievements; quiz XP is read from the quiz store.
            progress.recordPracticeDay()
            if (elapsed > 0) progress.recordStudySeconds(elapsed.toLong())
            val newAchievements = (progress.unlockedAchievements() - achievementsBefore).toList()
            _screen.value = QuizScreen.Result(
                plan = s.plan,
                correctCount = correct,
                passed = passed,
                elapsedSeconds = elapsed,
                xpEarned = xpEarned,
                quizXpBefore = quizXpBefore,
                quizXpGoal = MAX_LEVEL * XpEngine.QUIZ_LEVEL_MAX,
                newAchievements = newAchievements,
            )
        }
    }

    // ── result ────────────────────────────────────────────────────────────────

    /** Retry a failed level: same five questions, reshuffled (§5) — no new tutorial. */
    fun retryLevel() {
        val s = _screen.value as? QuizScreen.Result ?: return
        val plan = QuizGenerator.reshuffleForRetry(s.plan)
        val answers = freshAnswers(plan)
        repo.persistSession(QuizSession(plan, LevelPhase.QUIZ, 0, answers))
        quizStartMillis = System.currentTimeMillis()
        _screen.value = QuizScreen.Quiz(plan, 0, answers)
    }

    /** "Proceed to next lesson" — start the next level after a pass, else go to the map. */
    fun proceedToNext() {
        val s = _screen.value as? QuizScreen.Result ?: return
        val next = s.plan.level + 1
        if (s.passed && next <= MAX_LEVEL && repo.statusOf(next) != LevelStatus.LOCKED) {
            startLevel(next)
        } else {
            showMap(offerResume = false)
        }
    }

    fun finishResult() = showMap(offerResume = false)

    private fun freshAnswers(plan: LevelPlan): List<Boolean?> = List(plan.questions.size) { null }

    private companion object {
        /**
         * Whether the "Continue last session?" popup has already been offered this
         * app process. Static so it survives the ViewModel being recreated each
         * time the Game tab is re-entered — the popup then shows at most once.
         */
        @Volatile
        private var resumePromptConsumed = false
    }
}

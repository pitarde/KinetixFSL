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
import com.example.kinetixfsl.game.model.SIGNS_PER_LEVEL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One node on the level roadmap. */
data class LevelCard(val level: Int, val status: LevelStatus, val title: String)

/** What the whole Quiz Game tab is showing right now. */
sealed interface QuizScreen {
    data object Loading : QuizScreen

    data class Map(
        val levels: List<LevelCard>,
        /** The level the "CURRENT GOAL" card points at (a resume target if any). */
        val goalLevel: Int,
        val goalTitle: String,
        /** Progress through [goalLevel]'s attempt, styled as XP (0..[xpGoal]). */
        val goalXp: Int,
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
        // current level. XP mirrors real progress through that level's attempt:
        // 10 steps (5 videos + 5 questions) × 10 = 100.
        val goalLevel = pending?.plan?.level ?: current
        val goalXp = pending?.takeIf { it.plan.level == goalLevel }?.let { stepsDone(it) * 10 } ?: 0

        _screen.value = QuizScreen.Map(
            levels = (1..MAX_LEVEL).map { LevelCard(it, repo.statusOf(it), levelTitle(it)) },
            goalLevel = goalLevel,
            goalTitle = levelTitle(goalLevel),
            goalXp = goalXp,
            xpGoal = XP_PER_LEVEL,
            canResume = pending != null,
            passedCount = repo.passedCount(),
            resumeSession = if (offerResume) pending else null,
        )
    }

    /** Steps completed in an attempt: full videos watched, then questions reached. */
    private fun stepsDone(session: QuizSession): Int = when (session.phase) {
        LevelPhase.VIDEO -> session.index
        LevelPhase.QUIZ -> SIGNS_PER_LEVEL + session.index
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
            repo.completeLevel(s.plan.level, correct, passed)
            // Finishing a quiz counts as a practice day (streak), logs study time,
            // and may unlock achievements; quiz XP is read from the quiz store.
            progress.recordPracticeDay()
            if (elapsed > 0) progress.recordStudySeconds(elapsed.toLong())
            _screen.value = QuizScreen.Result(s.plan, correct, passed, elapsed)
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
        /** Display-only XP goal per level for the roadmap card (XP scaling is TBD, §8). */
        const val XP_PER_LEVEL = 100

        /**
         * Whether the "Continue last session?" popup has already been offered this
         * app process. Static so it survives the ViewModel being recreated each
         * time the Game tab is re-entered — the popup then shows at most once.
         */
        @Volatile
        private var resumePromptConsumed = false
    }
}

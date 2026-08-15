package com.example.kinetixfsl.progress

import android.content.Context
import com.example.kinetixfsl.game.data.QuizStore
import com.example.kinetixfsl.modules.model.FslSignData
import java.time.LocalDate
import java.time.LocalTime

/**
 * The single owner of account progress and XP. Aggregates the local progress
 * store, the quiz store (first-clear scores), and the static category content,
 * and computes a [PlayerProgress] snapshot through [XpEngine].
 *
 * Everything is local and synchronous, matching the app's offline-first quiz.
 * Callers record events (a sign learned, a practice day) and then read a fresh
 * snapshot for the Profile / Dashboard.
 */
class ProgressRepository(context: Context) {

    private val store = ProgressStore(context)
    private val quizStore = QuizStore(context)
    private val activityStore = ActivityLogStore(context)
    private var state: ProgressState = store.load()
    private var activity: ActivityLogState = activityStore.load()

    /** The raw event log, for the analytics layer to read. */
    fun activityLog(): ActivityLogState = activity

    /** The set of sign ids the user has learned (for per-sign completion UI). */
    fun learnedSigns(): Set<String> = state.learnedSignIds

    /**
     * The set of currently-unlocked achievements. Runs a refresh first so it
     * reflects any just-recorded events — capture this before and after an event
     * and diff to find what was *newly* unlocked (for the unlock animation).
     */
    fun unlockedAchievements(): Set<Achievement> {
        refreshAchievements()
        return Achievement.entries.filter { it.name in state.unlockedAchievements }.toSet()
    }

    private fun today(): Long = LocalDate.now().toEpochDay()

    /** Mark a day/hour as active (streak-risk, heatmap, best-time insight). */
    private fun markActive(day: Long) {
        val hour = LocalTime.now().hour.coerceIn(0, 23)
        val hist = activity.hourHistogram.toMutableList().also { it[hour] = it[hour] + 1 }
        activity = activity.copy(activeDays = activity.activeDays + day, hourHistogram = hist)
    }

    /** Ordered item ids per category — the ordering fixes where the 400-remainder lands. */
    private val categoryItems: List<Pair<CategoryRef, List<String>>> =
        FslSignData.categories.map { c ->
            CategoryRef(c.id, c.title) to c.signs.map { it.id }
        }

    private data class CategoryRef(val id: String, val title: String)

    // ── events ──────────────────────────────────────────────────────────────

    /** Called when the user successfully performs a sign in Camera Practice. */
    fun recordSignLearned(signId: String, categoryId: String = "") {
        if (signId.isBlank()) return
        val day = today()
        val firstTime = signId !in state.learnedSignIds
        state = state.copy(learnedSignIds = state.learnedSignIds + signId)
        activity = activity.copy(
            // Only count a sign toward "learned today" the first time it's learned.
            dailyLearned = if (firstTime) {
                activity.dailyLearned + (day to (activity.dailyLearned[day] ?: 0) + 1)
            } else activity.dailyLearned,
            signLastPracticed = activity.signLastPracticed + (signId to day),
            lessonsCompleted = if (categoryId.isNotBlank()) {
                activity.lessonsCompleted + (categoryId to (activity.lessonsCompleted[categoryId] ?: 0) + 1)
            } else activity.lessonsCompleted,
        )
        markActive(day)
        recordPracticeDay(persist = false) // learning is also a practice day
        persistAll()
    }

    /** Called when a Camera Practice session is entered (a lesson attempt). */
    fun recordLessonStarted(categoryId: String) {
        if (categoryId.isBlank()) return
        activity = activity.copy(
            lessonsStarted = activity.lessonsStarted + (categoryId to (activity.lessonsStarted[categoryId] ?: 0) + 1),
        )
        saveActivity()
    }

    /** Accumulate real study time (from camera sessions and quiz attempts). */
    fun recordStudySeconds(seconds: Long) {
        if (seconds <= 0) return
        val day = today()
        activity = activity.copy(
            dailyStudySeconds = activity.dailyStudySeconds + (day to (activity.dailyStudySeconds[day] ?: 0L) + seconds),
        )
        markActive(day)
        saveActivity()
    }

    /** Log a wrong quiz answer for confusion-pair analytics. */
    fun recordQuizMistake(targetSignId: String, targetWord: String, chosenWord: String) {
        if (targetWord.isBlank() || chosenWord.isBlank()) return
        val entry = QuizMistake(targetSignId, targetWord, chosenWord, today())
        activity = activity.copy(mistakes = (activity.mistakes + entry).takeLast(MAX_MISTAKES))
        saveActivity()
    }

    /**
     * Log the outcome of a quiz answer: accuracy per hour (best-time insight) and
     * per category (adaptive difficulty).
     */
    fun recordQuizAnswer(categoryId: String, correct: Boolean) {
        val hour = LocalTime.now().hour.coerceIn(0, 23)
        val hc = activity.hourCorrect.toMutableList()
        val ha = activity.hourAttempts.toMutableList()
        ha[hour] = ha[hour] + 1
        if (correct) hc[hour] = hc[hour] + 1
        val catCorrect = activity.categoryCorrect.toMutableMap()
        val catAttempts = activity.categoryAttempts.toMutableMap()
        if (categoryId.isNotBlank()) {
            catAttempts[categoryId] = (catAttempts[categoryId] ?: 0) + 1
            if (correct) catCorrect[categoryId] = (catCorrect[categoryId] ?: 0) + 1
        }
        activity = activity.copy(
            hourCorrect = hc, hourAttempts = ha,
            categoryCorrect = catCorrect, categoryAttempts = catAttempts,
        )
        saveActivity()
    }

    /** Log a Camera-Practice failure reason: "handshape" / "motion" / "timing". */
    fun recordCameraError(type: String) {
        if (type.isBlank()) return
        val errs = activity.cameraErrors.toMutableMap()
        errs[type] = (errs[type] ?: 0) + 1
        activity = activity.copy(cameraErrors = errs)
        saveActivity()
    }

    /**
     * Marks today as a practice day: advances the streak, awards streak
     * milestones at each 7-day boundary (cap 6), and flags a comeback after a
     * gap of 3+ days. Idempotent within the same calendar day.
     */
    fun recordPracticeDay(persist: Boolean = true) {
        val day = today()
        val last = state.streakLastEpochDay
        if (day != last) {
            val newStreak = when {
                last < 0 -> 1
                day - last == 1L -> state.currentStreak + 1
                else -> 1
            }
            val comeback = state.hadComeback || (last >= 0 && day - last >= 3)
            var milestones = state.streakMilestones
            // Award a milestone each time a running streak crosses a 7-day mark.
            if (newStreak > 0 && newStreak % 7 == 0 && milestones < XpEngine.STREAK_MAX_MILESTONES) {
                milestones += 1
            }
            state = state.copy(
                streakLastEpochDay = day,
                currentStreak = newStreak,
                bestStreak = maxOf(state.bestStreak, newStreak),
                streakMilestones = milestones,
                hadComeback = comeback,
            )
        }
        markActive(day)
        refreshAchievements()
        if (persist) persistAll()
    }

    /** Re-evaluate achievements (e.g. after a quiz clear) and persist. */
    fun refresh() {
        refreshAchievements()
        persist()
    }

    // ── snapshot ──────────────────────────────────────────────────────────────

    fun snapshot(): PlayerProgress {
        refreshAchievements() // keep unlocks current on every read
        val quiz = quizStore.load()

        val categories = categoryItems.map { (ref, ids) ->
            val xp = XpEngine.categoryXp(ids, state.learnedSignIds)
            CategoryMasteryInfo(
                id = ref.id,
                name = ref.title,
                xp = xp,
                fraction = xp / XpEngine.CATEGORY_MAX.toFloat(),
                learned = ids.count { it in state.learnedSignIds },
                total = ids.size,
            )
        }

        val categoryXp = categories.sumOf { it.xp }
        val quizXp = XpEngine.quizXp(quiz.bestScores)
        val streakXp = XpEngine.streakXp(state.streakMilestones)
        val achievementXp = XpEngine.achievementXp(state.unlockedAchievements.size)
        val accountXpRaw = categoryXp + quizXp + streakXp + achievementXp
        val level = XpEngine.levelForXp(accountXpRaw)

        return PlayerProgress(
            accountXpRaw = accountXpRaw,
            level = level,
            levelProgress = XpEngine.levelProgress(accountXpRaw),
            xpIntoLevel = XpEngine.xpIntoLevel(accountXpRaw),
            maxObtainable = XpEngine.MAX_OBTAINABLE_XP,
            rank = RankTier.forLevel(level),
            streakDays = state.currentStreak,
            streakMilestones = state.streakMilestones,
            signsLearned = state.learnedSignIds.size,
            quizLevelsCleared = quiz.firstClearScores.size,
            categoryXp = categoryXp,
            quizXp = quizXp,
            streakXp = streakXp,
            achievementXp = achievementXp,
            categories = categories,
            achievements = Achievement.entries.map {
                AchievementView(it, it.name in state.unlockedAchievements)
            },
        )
    }

    // ── achievements ──────────────────────────────────────────────────────────

    /**
     * Unlocks any newly-satisfied achievements. Runs to a fixpoint because
     * unlocking an achievement adds XP, which can raise Level and satisfy the
     * "reach Level 10" achievement in the same pass. Unlocks are sticky.
     */
    private fun refreshAchievements() {
        val quiz = quizStore.load()
        val categoryXps = categoryItems.map { (_, ids) -> XpEngine.categoryXp(ids, state.learnedSignIds) }
        val masteredCount = categoryXps.count { it >= XpEngine.CATEGORY_MAX }
        val categoryXpTotal = categoryXps.sum()
        val quizXp = XpEngine.quizXp(quiz.bestScores)
        val streakXp = XpEngine.streakXp(state.streakMilestones)

        val unlocked = state.unlockedAchievements.toMutableSet()
        while (true) {
            val accountXp = categoryXpTotal + quizXp + streakXp +
                XpEngine.achievementXp(unlocked.size)
            val level = XpEngine.levelForXp(accountXp)

            val satisfied = buildSet {
                if (state.learnedSignIds.isNotEmpty()) add(Achievement.FIRST_LESSON)
                if (quiz.firstClearScores.isNotEmpty()) add(Achievement.FIRST_QUIZ)
                if (quiz.bestScores.values.any { it >= XpEngine.QUIZ_QUESTIONS }) add(Achievement.PERFECT_QUIZ)
                if (masteredCount >= 1) add(Achievement.MASTER_ONE)
                if (masteredCount >= 3) add(Achievement.MASTER_THREE)
                if (masteredCount >= 7) add(Achievement.MASTER_ALL)
                if (quiz.firstClearScores.size >= 10) add(Achievement.CLEAR_ALL_QUIZ)
                if (state.bestStreak >= 3) add(Achievement.STREAK_3)
                if (level >= 10) add(Achievement.LEVEL_10)
                if (state.hadComeback) add(Achievement.COMEBACK)
            }.map { it.name }

            val newlyUnlocked = satisfied.filterNot { it in unlocked }
            if (newlyUnlocked.isEmpty()) break
            unlocked += newlyUnlocked
        }

        if (unlocked != state.unlockedAchievements) {
            state = state.copy(unlockedAchievements = unlocked)
        }
    }

    private fun persist() {
        store.save(state)
        scheduleCloudSync()
    }

    private fun persistAll() {
        store.save(state)
        activityStore.save(activity)
        scheduleCloudSync()
    }

    /** Save the activity log AND mirror it to the cloud (debounced). */
    private fun saveActivity() {
        activityStore.save(activity)
        scheduleCloudSync()
    }

    /**
     * Builds the compact per-user document and hands it to [ProgressSync], which
     * debounces and writes it to Firestore. Flat summary fields let the admin
     * webpage sort/filter without parsing; the two JSON blobs are the exact
     * local state, so a new device restores byte-for-byte.
     */
    private fun scheduleCloudSync() {
        val snap = snapshot()
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        ProgressSync.schedulePush(
            mapOf(
                "uid" to (user?.uid ?: "guest"),
                "displayName" to (user?.displayName ?: ""),
                "email" to (user?.email ?: ""),
                // ── Descriptive summary (admin at-a-glance) ──
                "level" to snap.level,
                "accountXp" to snap.accountXpRaw,
                "rank" to snap.rank.title,
                "streakDays" to snap.streakDays,
                "bestStreak" to state.bestStreak,
                "signsLearned" to snap.signsLearned,
                "quizLevelsCleared" to snap.quizLevelsCleared,
                "achievementsUnlocked" to snap.unlockedAchievementCount,
                "lastActiveEpochDay" to state.streakLastEpochDay,
                "categories" to snap.categories.map {
                    mapOf(
                        "id" to it.id, "name" to it.name,
                        "learned" to it.learned, "total" to it.total,
                    )
                },
                // ── Raw local state (for the app's own cross-device restore
                //    and for the admin's diagnostic/predictive analytics) ──
                "progressJson" to (store.rawJson() ?: ""),
                "activityJson" to (activityStore.rawJson() ?: ""),
                "quizJson" to (quizStore.rawJson() ?: ""),
            )
        )
    }

    private companion object {
        /** Cap on retained quiz mistakes (recent history is enough for analytics). */
        const val MAX_MISTAKES = 300
    }
}

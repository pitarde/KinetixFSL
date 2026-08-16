package com.example.kinetixfsl.progress

import android.content.Context
import com.example.kinetixfsl.data.local.ActivityDayEntity
import com.example.kinetixfsl.data.local.ActivityHourEntity
import com.example.kinetixfsl.data.local.CameraErrorEntity
import com.example.kinetixfsl.data.local.CategoryQuizStatEntity
import com.example.kinetixfsl.data.local.KinetixDatabase
import com.example.kinetixfsl.data.local.LessonCountEntity
import com.example.kinetixfsl.data.local.QuizMistakeEntity
import com.example.kinetixfsl.data.local.SignLastPracticedEntity
import org.json.JSONArray
import org.json.JSONObject

/** One wrong quiz answer, for confusion-pair and error-type analytics. */
data class QuizMistake(
    val targetSignId: String,
    val targetWord: String,
    val chosenWord: String,
    val epochDay: Long,
)

/**
 * The raw event log behind the Profile analytics — per-day learning and study
 * time, per-sign last-practice dates, lesson start/finish counts, an hour-of-day
 * histogram, and recent quiz mistakes. Everything the descriptive/diagnostic/
 * predictive/prescriptive tabs are computed from.
 */
data class ActivityLogState(
    /** epochDay → signs learned that day (for weekly bars). */
    val dailyLearned: Map<Long, Int> = emptyMap(),
    /** epochDay → study seconds that day (for study time + heatmap intensity). */
    val dailyStudySeconds: Map<Long, Long> = emptyMap(),
    /** Every day with any activity (for streak-risk and heatmap). */
    val activeDays: Set<Long> = emptySet(),
    /** Count of activity sessions per hour 0..23 (for best-time insight). */
    val hourHistogram: List<Int> = List(24) { 0 },
    /** signId → epochDay last practiced (for skill decay). */
    val signLastPracticed: Map<String, Long> = emptyMap(),
    /** categoryId → lessons started / completed (for module drop-off). */
    val lessonsStarted: Map<String, Int> = emptyMap(),
    val lessonsCompleted: Map<String, Int> = emptyMap(),
    /** Recent wrong quiz answers, newest last, capped. */
    val mistakes: List<QuizMistake> = emptyList(),
    /** Quiz answers per hour 0..23 (for the best-time-by-accuracy insight). */
    val hourCorrect: List<Int> = List(24) { 0 },
    val hourAttempts: List<Int> = List(24) { 0 },
    /** Quiz answers per category (for adaptive-difficulty accuracy). */
    val categoryCorrect: Map<String, Int> = emptyMap(),
    val categoryAttempts: Map<String, Int> = emptyMap(),
    /** Camera-Practice failure reasons: "handshape" / "motion" / "timing". */
    val cameraErrors: Map<String, Int> = emptyMap(),
)

/**
 * Room/SQLite persistence for [ActivityLogState] (was SharedPreferences+JSON).
 * Public API unchanged; the state is spread across the typed activity tables
 * (`activity_day`, `activity_hour`, `sign_last_practiced`, `lesson_count`,
 * `category_quiz_stat`, `camera_error`, `quiz_mistake`), while [rawJson]/
 * [saveRawJson] keep the exact cloud-document JSON shape.
 */
class ActivityLogStore(context: Context) {

    private val dao = KinetixDatabase.get(context).activityDao()

    private val legacyPrefs = context.applicationContext
        .getSharedPreferences(UserScope.prefs("kinetix_activity"), Context.MODE_PRIVATE)

    fun load(): ActivityLogState {
        val uid = UserScope.uid()
        migrateLegacyIfNeeded(uid)

        val days = dao.days(uid)
        val hist = MutableList(24) { 0 }
        val correct = MutableList(24) { 0 }
        val attempts = MutableList(24) { 0 }
        dao.hours(uid).forEach {
            if (it.hour in 0..23) {
                hist[it.hour] = it.histogram
                correct[it.hour] = it.correct
                attempts[it.hour] = it.attempts
            }
        }
        val lessons = dao.lessonCounts(uid)
        val catStats = dao.categoryStats(uid)
        return ActivityLogState(
            dailyLearned = days.filter { it.learnedCount != 0 }.associate { it.epochDay to it.learnedCount },
            dailyStudySeconds = days.filter { it.studySeconds != 0L }.associate { it.epochDay to it.studySeconds },
            activeDays = days.filter { it.active }.map { it.epochDay }.toSet(),
            hourHistogram = hist,
            signLastPracticed = dao.signLastPracticed(uid).associate { it.signId to it.epochDay },
            lessonsStarted = lessons.filter { it.started != 0 }.associate { it.categoryId to it.started },
            lessonsCompleted = lessons.filter { it.completed != 0 }.associate { it.categoryId to it.completed },
            mistakes = dao.mistakes(uid).map { QuizMistake(it.targetSignId, it.targetWord, it.chosenWord, it.epochDay) },
            hourCorrect = correct,
            hourAttempts = attempts,
            categoryCorrect = catStats.filter { it.correct != 0 }.associate { it.categoryId to it.correct },
            categoryAttempts = catStats.filter { it.attempts != 0 }.associate { it.categoryId to it.attempts },
            cameraErrors = dao.cameraErrors(uid).associate { it.errorType to it.count },
        )
    }

    fun save(state: ActivityLogState) {
        val uid = UserScope.uid()
        val dayKeys = state.dailyLearned.keys + state.dailyStudySeconds.keys + state.activeDays
        val lessonKeys = state.lessonsStarted.keys + state.lessonsCompleted.keys
        val catKeys = state.categoryCorrect.keys + state.categoryAttempts.keys
        dao.replaceAll(
            uid = uid,
            days = dayKeys.map {
                ActivityDayEntity(
                    uid, it,
                    learnedCount = state.dailyLearned[it] ?: 0,
                    studySeconds = state.dailyStudySeconds[it] ?: 0L,
                    active = it in state.activeDays,
                )
            },
            hours = (0..23).map {
                ActivityHourEntity(
                    uid, it,
                    histogram = state.hourHistogram.getOrElse(it) { 0 },
                    correct = state.hourCorrect.getOrElse(it) { 0 },
                    attempts = state.hourAttempts.getOrElse(it) { 0 },
                )
            },
            signLastPracticed = state.signLastPracticed.map { (k, v) -> SignLastPracticedEntity(uid, k, v) },
            lessonCounts = lessonKeys.map {
                LessonCountEntity(uid, it, state.lessonsStarted[it] ?: 0, state.lessonsCompleted[it] ?: 0)
            },
            categoryStats = catKeys.map {
                CategoryQuizStatEntity(uid, it, state.categoryCorrect[it] ?: 0, state.categoryAttempts[it] ?: 0)
            },
            cameraErrors = state.cameraErrors.map { (k, v) -> CameraErrorEntity(uid, k, v) },
            mistakes = state.mistakes.map {
                QuizMistakeEntity(
                    uid = uid,
                    targetSignId = it.targetSignId,
                    targetWord = it.targetWord,
                    chosenWord = it.chosenWord,
                    epochDay = it.epochDay,
                )
            },
        )
    }

    /** The current state as the cloud-document JSON (never null). */
    fun rawJson(): String = toJson(load()).toString()

    /** Restores from the cloud-document JSON into the local database. */
    fun saveRawJson(json: String) {
        runCatching { save(parse(JSONObject(json))) }
    }

    private fun migrateLegacyIfNeeded(uid: String) {
        if (dao.rowCount(uid) > 0) return
        val json = legacyPrefs.getString(KEY, null) ?: return
        runCatching { save(parse(JSONObject(json))) }
        legacyPrefs.edit().remove(KEY).apply()
    }

    private fun toJson(s: ActivityLogState): JSONObject = JSONObject().apply {
        put("dailyLearned", longIntMap(s.dailyLearned))
        put("dailyStudySeconds", longLongMap(s.dailyStudySeconds))
        put("activeDays", JSONArray(s.activeDays.toList()))
        put("hourHistogram", JSONArray(s.hourHistogram))
        put("signLastPracticed", strLongMap(s.signLastPracticed))
        put("lessonsStarted", strIntMap(s.lessonsStarted))
        put("lessonsCompleted", strIntMap(s.lessonsCompleted))
        put("mistakes", JSONArray().apply {
            s.mistakes.forEach {
                put(JSONObject().apply {
                    put("t", it.targetSignId); put("tw", it.targetWord)
                    put("c", it.chosenWord); put("d", it.epochDay)
                })
            }
        })
        put("hourCorrect", JSONArray(s.hourCorrect))
        put("hourAttempts", JSONArray(s.hourAttempts))
        put("categoryCorrect", strIntMap(s.categoryCorrect))
        put("categoryAttempts", strIntMap(s.categoryAttempts))
        put("cameraErrors", strIntMap(s.cameraErrors))
    }

    private fun parse(o: JSONObject): ActivityLogState = ActivityLogState(
        dailyLearned = o.optJSONObject("dailyLearned").toLongIntMap(),
        dailyStudySeconds = o.optJSONObject("dailyStudySeconds").toLongLongMap(),
        activeDays = o.optJSONArray("activeDays").toLongSet(),
        hourHistogram = o.optJSONArray("hourHistogram").toIntList(24),
        signLastPracticed = o.optJSONObject("signLastPracticed").toStrLongMap(),
        lessonsStarted = o.optJSONObject("lessonsStarted").toStrIntMap(),
        lessonsCompleted = o.optJSONObject("lessonsCompleted").toStrIntMap(),
        mistakes = o.optJSONArray("mistakes").toMistakes(),
        hourCorrect = o.optJSONArray("hourCorrect").toIntList(24),
        hourAttempts = o.optJSONArray("hourAttempts").toIntList(24),
        categoryCorrect = o.optJSONObject("categoryCorrect").toStrIntMap(),
        categoryAttempts = o.optJSONObject("categoryAttempts").toStrIntMap(),
        cameraErrors = o.optJSONObject("cameraErrors").toStrIntMap(),
    )

    // ── serialisation helpers ────────────────────────────────────────────────

    private fun longIntMap(m: Map<Long, Int>) = JSONObject().apply { m.forEach { (k, v) -> put(k.toString(), v) } }
    private fun longLongMap(m: Map<Long, Long>) = JSONObject().apply { m.forEach { (k, v) -> put(k.toString(), v) } }
    private fun strIntMap(m: Map<String, Int>) = JSONObject().apply { m.forEach { (k, v) -> put(k, v) } }
    private fun strLongMap(m: Map<String, Long>) = JSONObject().apply { m.forEach { (k, v) -> put(k, v) } }

    private fun JSONObject?.toLongIntMap(): Map<Long, Int> {
        if (this == null) return emptyMap()
        val r = mutableMapOf<Long, Int>()
        keys().forEach { k -> k.toLongOrNull()?.let { r[it] = optInt(k) } }
        return r
    }
    private fun JSONObject?.toLongLongMap(): Map<Long, Long> {
        if (this == null) return emptyMap()
        val r = mutableMapOf<Long, Long>()
        keys().forEach { k -> k.toLongOrNull()?.let { r[it] = optLong(k) } }
        return r
    }
    private fun JSONObject?.toStrIntMap(): Map<String, Int> {
        if (this == null) return emptyMap()
        val r = mutableMapOf<String, Int>()
        keys().forEach { k -> r[k] = optInt(k) }
        return r
    }
    private fun JSONObject?.toStrLongMap(): Map<String, Long> {
        if (this == null) return emptyMap()
        val r = mutableMapOf<String, Long>()
        keys().forEach { k -> r[k] = optLong(k) }
        return r
    }
    private fun JSONArray?.toLongSet(): Set<Long> =
        if (this == null) emptySet() else (0 until length()).map { optLong(it) }.toSet()
    private fun JSONArray?.toIntList(size: Int): List<Int> {
        val base = MutableList(size) { 0 }
        if (this != null) for (i in 0 until minOf(length(), size)) base[i] = optInt(i)
        return base
    }
    private fun JSONArray?.toMistakes(): List<QuizMistake> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i ->
            val o = optJSONObject(i) ?: return@mapNotNull null
            QuizMistake(o.optString("t"), o.optString("tw"), o.optString("c"), o.optLong("d"))
        }
    }

    private companion object {
        const val KEY = "activity_v1"
    }
}

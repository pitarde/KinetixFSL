package com.example.kinetixfsl.progress

import android.content.Context
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

/** Local JSON persistence for [ActivityLogState], same style as the other stores. */
class ActivityLogStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(UserScope.prefs("kinetix_activity"), Context.MODE_PRIVATE)

    fun load(): ActivityLogState {
        val json = prefs.getString(KEY, null) ?: return ActivityLogState()
        return runCatching { parse(JSONObject(json)) }.getOrDefault(ActivityLogState())
    }

    fun save(state: ActivityLogState) {
        prefs.edit().putString(KEY, toJson(state).toString()).apply()
    }

    /** The exact stored JSON, for mirroring to the cloud. Null if nothing saved. */
    fun rawJson(): String? = prefs.getString(KEY, null)

    /** Writes cloud-restored JSON straight back, byte-for-byte (no re-parse). */
    fun saveRawJson(json: String) {
        prefs.edit().putString(KEY, json).apply()
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

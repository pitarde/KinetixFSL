package com.example.kinetixfsl.progress

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The persisted, non-quiz progress state (quiz clears live in the quiz's own
 * store). Everything XP is *derived* from this via [XpEngine] — only the sticky
 * facts are stored here: what's been learned, the streak, unlocked achievements.
 *
 * @param streakLastEpochDay last practice day as `LocalDate.toEpochDay()`, or -1.
 */
data class ProgressState(
    val learnedSignIds: Set<String> = emptySet(),
    val streakLastEpochDay: Long = -1,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val streakMilestones: Int = 0,
    /** Which streak days (1..6) the user has explicitly claimed for XP. Streak XP
     *  is derived from THIS, so a day's 500 XP only lands when the user taps
     *  Claim — and is never revoked once banked. */
    val claimedStreakDays: Set<Int> = emptySet(),
    val hadComeback: Boolean = false,
    val unlockedAchievements: Set<String> = emptySet(),
)

/**
 * Local, offline-first persistence for account progress — SharedPreferences +
 * JSON, mirroring the quiz's [com.example.kinetixfsl.game.data.QuizStore] so no
 * Room/backend is needed in this pass.
 */
class ProgressStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(UserScope.prefs("kinetix_progress"), Context.MODE_PRIVATE)

    fun load(): ProgressState {
        val json = prefs.getString(KEY, null) ?: return ProgressState()
        return runCatching { parse(JSONObject(json)) }.getOrDefault(ProgressState())
    }

    fun save(state: ProgressState) {
        prefs.edit().putString(KEY, toJson(state).toString()).apply()
    }

    /** The exact stored JSON, for mirroring to the cloud. Null if nothing saved. */
    fun rawJson(): String? = prefs.getString(KEY, null)

    /** Writes cloud-restored JSON straight back, byte-for-byte (no re-parse). */
    fun saveRawJson(json: String) {
        prefs.edit().putString(KEY, json).apply()
    }

    private fun toJson(s: ProgressState): JSONObject = JSONObject().apply {
        put("learnedSignIds", JSONArray(s.learnedSignIds.toList()))
        put("streakLastEpochDay", s.streakLastEpochDay)
        put("currentStreak", s.currentStreak)
        put("bestStreak", s.bestStreak)
        put("streakMilestones", s.streakMilestones)
        put("claimedStreakDays", JSONArray(s.claimedStreakDays.toList()))
        put("hadComeback", s.hadComeback)
        put("unlockedAchievements", JSONArray(s.unlockedAchievements.toList()))
    }

    private fun parse(o: JSONObject): ProgressState = ProgressState(
        learnedSignIds = o.optJSONArray("learnedSignIds").toStringSet(),
        streakLastEpochDay = o.optLong("streakLastEpochDay", -1),
        currentStreak = o.optInt("currentStreak", 0),
        bestStreak = o.optInt("bestStreak", 0),
        streakMilestones = o.optInt("streakMilestones", 0),
        // Migration: users from before claiming existed had streak XP granted
        // automatically per 7-day milestone. Seed those as already-claimed so
        // switching to the claim model never drops their banked XP or level.
        claimedStreakDays = if (o.has("claimedStreakDays"))
            o.optJSONArray("claimedStreakDays").toIntSet()
        else (1..o.optInt("streakMilestones", 0)).toSet(),
        hadComeback = o.optBoolean("hadComeback", false),
        unlockedAchievements = o.optJSONArray("unlockedAchievements").toStringSet(),
    )

    private fun JSONArray?.toStringSet(): Set<String> =
        if (this == null) emptySet() else (0 until length()).map { optString(it) }.toSet()

    private fun JSONArray?.toIntSet(): Set<Int> =
        if (this == null) emptySet() else (0 until length()).map { optInt(it) }.toSet()

    private companion object {
        const val KEY = "progress_v1"
    }
}

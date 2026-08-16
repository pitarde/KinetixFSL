package com.example.kinetixfsl.progress

import android.content.Context
import com.example.kinetixfsl.data.local.ClaimedStreakDayEntity
import com.example.kinetixfsl.data.local.KinetixDatabase
import com.example.kinetixfsl.data.local.LearnedSignEntity
import com.example.kinetixfsl.data.local.ProgressEntity
import com.example.kinetixfsl.data.local.UnlockedAchievementEntity
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
 * Local, offline-first persistence for account progress — now backed by the
 * Room/SQLite [KinetixDatabase] (was SharedPreferences+JSON). The public API is
 * unchanged so callers ([ProgressRepository], [ProgressSync]) don't change:
 *
 *  - [load]/[save] map the [ProgressState] to/from the typed `progress`,
 *    `learned_sign`, `claimed_streak_day` and `unlocked_achievement` tables;
 *  - [rawJson]/[saveRawJson] keep the exact JSON shape the cloud document uses,
 *    so cross-device restore stays byte-compatible with existing accounts.
 *
 * Data is scoped by the current account id ([UserScope.uid]); the first read for
 * an account transparently imports any pre-Room SharedPreferences blob.
 */
class ProgressStore(context: Context) {

    private val db = KinetixDatabase.get(context)
    private val dao = db.progressDao()

    // Kept only to import a pre-migration SharedPreferences blob once.
    private val legacyPrefs = context.applicationContext
        .getSharedPreferences(UserScope.prefs("kinetix_progress"), Context.MODE_PRIVATE)

    fun load(): ProgressState {
        val uid = UserScope.uid()
        migrateLegacyIfNeeded(uid)
        val p = dao.progress(uid)
        return ProgressState(
            learnedSignIds = dao.learnedSigns(uid).toSet(),
            streakLastEpochDay = p?.streakLastEpochDay ?: -1,
            currentStreak = p?.currentStreak ?: 0,
            bestStreak = p?.bestStreak ?: 0,
            streakMilestones = p?.streakMilestones ?: 0,
            claimedStreakDays = dao.claimedStreakDays(uid).toSet(),
            hadComeback = p?.hadComeback ?: false,
            unlockedAchievements = dao.unlockedAchievements(uid).toSet(),
        )
    }

    fun save(state: ProgressState) {
        val uid = UserScope.uid()
        dao.replaceAll(
            progress = ProgressEntity(
                uid = uid,
                streakLastEpochDay = state.streakLastEpochDay,
                currentStreak = state.currentStreak,
                bestStreak = state.bestStreak,
                streakMilestones = state.streakMilestones,
                hadComeback = state.hadComeback,
            ),
            learned = state.learnedSignIds.map { LearnedSignEntity(uid, it) },
            claimed = state.claimedStreakDays.map { ClaimedStreakDayEntity(uid, it) },
            achievements = state.unlockedAchievements.map { UnlockedAchievementEntity(uid, it) },
        )
    }

    /** The current state as the cloud-document JSON (never null; empty = defaults). */
    fun rawJson(): String = toJson(load()).toString()

    /** Restores from the cloud-document JSON into the local database. */
    fun saveRawJson(json: String) {
        runCatching { save(parse(JSONObject(json))) }
    }

    /** One-time import of the pre-Room SharedPreferences blob, if present. */
    private fun migrateLegacyIfNeeded(uid: String) {
        if (dao.rowCount(uid) > 0) return
        val json = legacyPrefs.getString(KEY, null) ?: return
        runCatching { save(parse(JSONObject(json))) }
        legacyPrefs.edit().remove(KEY).apply()
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

package com.example.kinetixfsl.game.data

import android.content.Context
import com.example.kinetixfsl.data.local.KinetixDatabase
import com.example.kinetixfsl.data.local.QuizFirstAssignedLevelEntity
import com.example.kinetixfsl.data.local.QuizPassedLevelEntity
import com.example.kinetixfsl.data.local.QuizProgressEntity
import com.example.kinetixfsl.data.local.QuizScoreEntity
import com.example.kinetixfsl.data.local.QuizUsedInitialPassEntity
import com.example.kinetixfsl.game.model.LevelPhase
import com.example.kinetixfsl.game.model.LevelPlan
import com.example.kinetixfsl.game.model.QuestionType
import com.example.kinetixfsl.game.model.QuizQuestion
import com.example.kinetixfsl.game.model.Tier
import com.example.kinetixfsl.progress.UserScope
import org.json.JSONArray
import org.json.JSONObject

/**
 * A resumable, in-progress level attempt (§6). Persisted whole so a resume
 * replays the exact same questions in the exact same order.
 *
 * @param answers per-question result so far — `true`/`false` once answered,
 *                `null` while still pending. Size == plan.questions.size.
 */
data class QuizSession(
    val plan: LevelPlan,
    val phase: LevelPhase,
    val index: Int,
    val answers: List<Boolean?>,
)

/** Everything about the player's quiz progress that must survive an app close. */
data class QuizProgress(
    val unlockedMaxLevel: Int = 1,
    val passedLevels: Set<Int> = emptySet(),
    val firstAssignedLevels: Set<Int> = emptySet(),
    val usedInitialPass: Map<Tier, Set<String>> = emptyMap(),
    /** Correct-answer count of each level's FIRST clear (level → 0..5). Used to
     *  count cleared levels; XP now tracks [bestScores] instead. */
    val firstClearScores: Map<Int, Int> = emptyMap(),
    /** Best correct-answer count ever achieved per level (level → 0..5). Quiz XP
     *  is 60 × best, so retaking and improving tops up the difference. */
    val bestScores: Map<Int, Int> = emptyMap(),
    val session: QuizSession? = null,
)

/**
 * Local, offline-first persistence for the Quiz Game, backed by
 * [android.content.SharedPreferences] with JSON payloads. This is the source of
 * truth during play; syncing unlocks/passes up to Firestore for the admin
 * dashboard is a separate, additive concern (see `docs/QUIZ_GAME.md`).
 *
 * SharedPreferences (rather than Room) keeps the persisted state — which is tiny
 * and only written at level boundaries and phase steps — free of any annotation
 * processor or new Gradle plugin, while still fully satisfying "must survive a
 * full app close" and "update locally even fully offline".
 */
class QuizStore(context: Context) {

    private val dao = KinetixDatabase.get(context).quizDao()

    private val legacyPrefs = context.applicationContext
        .getSharedPreferences(UserScope.prefs("quiz_game"), Context.MODE_PRIVATE)

    fun load(): QuizProgress {
        val uid = UserScope.uid()
        migrateLegacyIfNeeded(uid)
        val p = dao.progress(uid) ?: return QuizProgress()
        val scores = dao.scores(uid)
        val used = dao.usedInitialPass(uid)
            .groupBy { it.tier }
            .mapNotNull { (tierName, rows) ->
                runCatching { Tier.valueOf(tierName) }.getOrNull()?.let { it to rows.map { r -> r.signId }.toSet() }
            }
            .toMap()
        return QuizProgress(
            unlockedMaxLevel = p.unlockedMaxLevel.coerceAtLeast(1),
            passedLevels = dao.passedLevels(uid).toSet(),
            firstAssignedLevels = dao.firstAssignedLevels(uid).toSet(),
            usedInitialPass = used,
            firstClearScores = scores.filter { it.firstClearScore != null }
                .associate { it.level to it.firstClearScore!! },
            bestScores = scores.filter { it.bestScore != null }.associate { it.level to it.bestScore!! },
            session = p.sessionJson?.let { runCatching { parseSession(JSONObject(it)) }.getOrNull() },
        )
    }

    fun save(progress: QuizProgress) {
        val uid = UserScope.uid()
        val scoreLevels = progress.firstClearScores.keys + progress.bestScores.keys
        dao.replaceAll(
            progress = QuizProgressEntity(
                uid = uid,
                unlockedMaxLevel = progress.unlockedMaxLevel,
                sessionJson = progress.session?.let { sessionToJson(it).toString() },
            ),
            passed = progress.passedLevels.map { QuizPassedLevelEntity(uid, it) },
            firstAssigned = progress.firstAssignedLevels.map { QuizFirstAssignedLevelEntity(uid, it) },
            scores = scoreLevels.map {
                QuizScoreEntity(uid, it, progress.firstClearScores[it], progress.bestScores[it])
            },
            usedInitialPass = progress.usedInitialPass.flatMap { (tier, ids) ->
                ids.map { QuizUsedInitialPassEntity(uid, tier.name, it) }
            },
        )
    }

    fun clear() {
        dao.wipe(UserScope.uid())
    }

    /** The current state as the cloud-document JSON (never null). */
    fun rawJson(): String = progressToJson(load()).toString()

    /** Restores from the cloud-document JSON into the local database. */
    fun saveRawJson(json: String) {
        runCatching { save(parseProgress(JSONObject(json))) }
    }

    private fun migrateLegacyIfNeeded(uid: String) {
        if (dao.rowCount(uid) > 0) return
        val json = legacyPrefs.getString(KEY_PROGRESS, null) ?: return
        runCatching { save(parseProgress(JSONObject(json))) }
        legacyPrefs.edit().remove(KEY_PROGRESS).apply()
    }

    // ── serialisation ──────────────────────────────────────────────────────

    private fun progressToJson(p: QuizProgress): JSONObject = JSONObject().apply {
        put("unlockedMaxLevel", p.unlockedMaxLevel)
        put("passedLevels", JSONArray(p.passedLevels.toList()))
        put("firstAssignedLevels", JSONArray(p.firstAssignedLevels.toList()))
        put("usedInitialPass", JSONObject().apply {
            p.usedInitialPass.forEach { (tier, ids) -> put(tier.name, JSONArray(ids.toList())) }
        })
        put("firstClearScores", JSONObject().apply {
            p.firstClearScores.forEach { (level, score) -> put(level.toString(), score) }
        })
        put("bestScores", JSONObject().apply {
            p.bestScores.forEach { (level, score) -> put(level.toString(), score) }
        })
        p.session?.let { put("session", sessionToJson(it)) }
    }

    private fun parseProgress(o: JSONObject): QuizProgress = QuizProgress(
        unlockedMaxLevel = o.optInt("unlockedMaxLevel", 1).coerceAtLeast(1),
        passedLevels = o.optJSONArray("passedLevels").toIntSet(),
        firstAssignedLevels = o.optJSONArray("firstAssignedLevels").toIntSet(),
        usedInitialPass = o.optJSONObject("usedInitialPass").toUsedMap(),
        firstClearScores = o.optJSONObject("firstClearScores").toIntIntMap(),
        // Migrate: older saves have no bestScores, so seed from the first-clear
        // scores (the best a returning player has demonstrably scored so far).
        bestScores = if (o.has("bestScores")) o.optJSONObject("bestScores").toIntIntMap()
        else o.optJSONObject("firstClearScores").toIntIntMap(),
        session = o.optJSONObject("session")?.let { parseSession(it) },
    )

    private fun sessionToJson(s: QuizSession): JSONObject = JSONObject().apply {
        put("phase", s.phase.name)
        put("index", s.index)
        put("answers", JSONArray().apply {
            s.answers.forEach { put(if (it == null) JSONObject.NULL else it) }
        })
        put("plan", planToJson(s.plan))
    }

    private fun parseSession(o: JSONObject): QuizSession? {
        val plan = parsePlan(o.optJSONObject("plan") ?: return null) ?: return null
        val answersArr = o.optJSONArray("answers")
        val answers = (0 until (answersArr?.length() ?: 0)).map { i ->
            if (answersArr!!.isNull(i)) null else answersArr.optBoolean(i)
        }
        return QuizSession(
            plan = plan,
            phase = runCatching { LevelPhase.valueOf(o.optString("phase")) }
                .getOrDefault(LevelPhase.VIDEO),
            index = o.optInt("index", 0),
            answers = answers.ifEmpty { List(plan.questions.size) { null } },
        )
    }

    private fun planToJson(p: LevelPlan): JSONObject = JSONObject().apply {
        put("level", p.level)
        put("signIds", JSONArray(p.signIds))
        put("questions", JSONArray().apply { p.questions.forEach { put(questionToJson(it)) } })
    }

    private fun parsePlan(o: JSONObject): LevelPlan? {
        val level = o.optInt("level", -1).takeIf { it > 0 } ?: return null
        val signIds = o.optJSONArray("signIds").toStringList()
        val qArr = o.optJSONArray("questions") ?: return null
        val questions = (0 until qArr.length()).mapNotNull { parseQuestion(qArr.optJSONObject(it)) }
        if (questions.isEmpty()) return null
        return LevelPlan(level = level, signIds = signIds, questions = questions)
    }

    private fun questionToJson(q: QuizQuestion): JSONObject = JSONObject().apply {
        put("type", q.type.name)
        put("targetSignId", q.targetSignId)
        put("targetWord", q.targetWord)
        put("wordOptions", JSONArray(q.wordOptions))
        put("signOptions", JSONArray(q.signOptions))
        put("matchWords", JSONArray(q.matchWords))
        put("claimedWord", q.claimedWord)
        put("claimedIsCorrect", q.claimedIsCorrect)
        put("correctWord", q.correctWord)
        put("correctSignId", q.correctSignId)
    }

    private fun parseQuestion(o: JSONObject?): QuizQuestion? {
        if (o == null) return null
        val type = runCatching { QuestionType.valueOf(o.optString("type")) }.getOrNull() ?: return null
        return QuizQuestion(
            type = type,
            targetSignId = o.optString("targetSignId"),
            targetWord = o.optString("targetWord"),
            wordOptions = o.optJSONArray("wordOptions").toStringList(),
            signOptions = o.optJSONArray("signOptions").toStringList(),
            matchWords = o.optJSONArray("matchWords").toStringList(),
            claimedWord = o.optString("claimedWord"),
            claimedIsCorrect = o.optBoolean("claimedIsCorrect"),
            correctWord = o.optString("correctWord"),
            correctSignId = o.optString("correctSignId"),
        )
    }

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }

    private fun JSONArray?.toIntSet(): Set<Int> =
        if (this == null) emptySet() else (0 until length()).map { optInt(it) }.toSet()

    private fun JSONObject?.toIntIntMap(): Map<Int, Int> {
        if (this == null) return emptyMap()
        val result = mutableMapOf<Int, Int>()
        keys().forEach { key -> key.toIntOrNull()?.let { result[it] = optInt(key) } }
        return result
    }

    private fun JSONObject?.toUsedMap(): Map<Tier, Set<String>> {
        if (this == null) return emptyMap()
        val result = mutableMapOf<Tier, Set<String>>()
        keys().forEach { key ->
            runCatching { Tier.valueOf(key) }.getOrNull()?.let { tier ->
                result[tier] = optJSONArray(key).toStringList().toSet()
            }
        }
        return result
    }

    private companion object {
        const val KEY_PROGRESS = "progress_v1"
    }
}

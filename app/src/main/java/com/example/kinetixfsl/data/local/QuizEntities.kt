package com.example.kinetixfsl.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

// ─────────────────────────────────────────────────────────────────────────────
// Quiz-game tables. All scoped by `uid`.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One row per account: the furthest-unlocked level and the resumable in-progress
 * attempt. The attempt (a whole level plan with its five questions and their
 * option arrays) is deeply nested and purely transient — it exists only to
 * replay the exact same questions after an app kill — so it's kept as a JSON
 * blob rather than exploded into question/option tables that nothing queries.
 */
@Entity(tableName = "quiz_progress")
data class QuizProgressEntity(
    @PrimaryKey val uid: String,
    val unlockedMaxLevel: Int,
    val sessionJson: String?,
)

/** One row per passed level. */
@Entity(tableName = "quiz_passed_level", primaryKeys = ["uid", "level"])
data class QuizPassedLevelEntity(val uid: String, val level: Int)

/** One row per level that has been assigned its first-time sign set. */
@Entity(tableName = "quiz_first_assigned_level", primaryKeys = ["uid", "level"])
data class QuizFirstAssignedLevelEntity(val uid: String, val level: Int)

/**
 * Per-level scores. `firstClearScore` counts cleared levels; `bestScore` drives
 * XP (60 × best). Either may be null for a level present only in the other map.
 */
@Entity(tableName = "quiz_score", primaryKeys = ["uid", "level"])
data class QuizScoreEntity(
    val uid: String,
    val level: Int,
    val firstClearScore: Int?,
    val bestScore: Int?,
)

/** Signs already used in a tier's first-time unlock pass (tier → signIds). */
@Entity(tableName = "quiz_used_initial_pass", primaryKeys = ["uid", "tier", "signId"])
data class QuizUsedInitialPassEntity(val uid: String, val tier: String, val signId: String)

@Dao
interface QuizDao {

    @Query("SELECT COUNT(*) FROM quiz_progress WHERE uid = :uid")
    fun rowCount(uid: String): Int

    @Query("SELECT * FROM quiz_progress WHERE uid = :uid")
    fun progress(uid: String): QuizProgressEntity?

    @Query("SELECT level FROM quiz_passed_level WHERE uid = :uid")
    fun passedLevels(uid: String): List<Int>

    @Query("SELECT level FROM quiz_first_assigned_level WHERE uid = :uid")
    fun firstAssignedLevels(uid: String): List<Int>

    @Query("SELECT * FROM quiz_score WHERE uid = :uid")
    fun scores(uid: String): List<QuizScoreEntity>

    @Query("SELECT * FROM quiz_used_initial_pass WHERE uid = :uid")
    fun usedInitialPass(uid: String): List<QuizUsedInitialPassEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertProgress(row: QuizProgressEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertPassed(rows: List<QuizPassedLevelEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertFirstAssigned(rows: List<QuizFirstAssignedLevelEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertScores(rows: List<QuizScoreEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertUsedInitialPass(rows: List<QuizUsedInitialPassEntity>)

    @Query("DELETE FROM quiz_progress WHERE uid = :uid") fun deleteProgress(uid: String)
    @Query("DELETE FROM quiz_passed_level WHERE uid = :uid") fun deletePassed(uid: String)
    @Query("DELETE FROM quiz_first_assigned_level WHERE uid = :uid") fun deleteFirstAssigned(uid: String)
    @Query("DELETE FROM quiz_score WHERE uid = :uid") fun deleteScores(uid: String)
    @Query("DELETE FROM quiz_used_initial_pass WHERE uid = :uid") fun deleteUsedInitialPass(uid: String)

    @Transaction
    fun replaceAll(
        progress: QuizProgressEntity,
        passed: List<QuizPassedLevelEntity>,
        firstAssigned: List<QuizFirstAssignedLevelEntity>,
        scores: List<QuizScoreEntity>,
        usedInitialPass: List<QuizUsedInitialPassEntity>,
    ) {
        deletePassed(progress.uid)
        deleteFirstAssigned(progress.uid)
        deleteScores(progress.uid)
        deleteUsedInitialPass(progress.uid)
        upsertProgress(progress)
        insertPassed(passed)
        insertFirstAssigned(firstAssigned)
        insertScores(scores)
        insertUsedInitialPass(usedInitialPass)
    }

    @Transaction
    fun wipe(uid: String) {
        deleteProgress(uid)
        deletePassed(uid)
        deleteFirstAssigned(uid)
        deleteScores(uid)
        deleteUsedInitialPass(uid)
    }
}

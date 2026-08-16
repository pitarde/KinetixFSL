package com.example.kinetixfsl.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

// ─────────────────────────────────────────────────────────────────────────────
// Account-progress tables. Everything is scoped by `uid` (the Firebase account
// id, or "guest" when signed out) so one shared database still isolates accounts.
// ─────────────────────────────────────────────────────────────────────────────

/** One row per account: the streak scalars. */
@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val uid: String,
    val streakLastEpochDay: Long,
    val currentStreak: Int,
    val bestStreak: Int,
    val streakMilestones: Int,
    val hadComeback: Boolean,
)

/** One row per learned sign. */
@Entity(tableName = "learned_sign", primaryKeys = ["uid", "signId"])
data class LearnedSignEntity(val uid: String, val signId: String)

/** One row per claimed streak day (1..6). */
@Entity(tableName = "claimed_streak_day", primaryKeys = ["uid", "day"])
data class ClaimedStreakDayEntity(val uid: String, val day: Int)

/** One row per unlocked achievement (the enum name). */
@Entity(tableName = "unlocked_achievement", primaryKeys = ["uid", "achievement"])
data class UnlockedAchievementEntity(val uid: String, val achievement: String)

@Dao
interface ProgressDao {

    @Query("SELECT COUNT(*) FROM progress WHERE uid = :uid")
    fun rowCount(uid: String): Int

    @Query("SELECT * FROM progress WHERE uid = :uid")
    fun progress(uid: String): ProgressEntity?

    @Query("SELECT signId FROM learned_sign WHERE uid = :uid")
    fun learnedSigns(uid: String): List<String>

    @Query("SELECT day FROM claimed_streak_day WHERE uid = :uid")
    fun claimedStreakDays(uid: String): List<Int>

    @Query("SELECT achievement FROM unlocked_achievement WHERE uid = :uid")
    fun unlockedAchievements(uid: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertProgress(row: ProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertLearnedSigns(rows: List<LearnedSignEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertClaimedDays(rows: List<ClaimedStreakDayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAchievements(rows: List<UnlockedAchievementEntity>)

    @Query("DELETE FROM progress WHERE uid = :uid")
    fun deleteProgress(uid: String)

    @Query("DELETE FROM learned_sign WHERE uid = :uid")
    fun deleteLearnedSigns(uid: String)

    @Query("DELETE FROM claimed_streak_day WHERE uid = :uid")
    fun deleteClaimedDays(uid: String)

    @Query("DELETE FROM unlocked_achievement WHERE uid = :uid")
    fun deleteAchievements(uid: String)

    /** Replaces this account's whole progress state in one transaction. */
    @Transaction
    fun replaceAll(
        progress: ProgressEntity,
        learned: List<LearnedSignEntity>,
        claimed: List<ClaimedStreakDayEntity>,
        achievements: List<UnlockedAchievementEntity>,
    ) {
        deleteLearnedSigns(progress.uid)
        deleteClaimedDays(progress.uid)
        deleteAchievements(progress.uid)
        upsertProgress(progress)
        insertLearnedSigns(learned)
        insertClaimedDays(claimed)
        insertAchievements(achievements)
    }

    /** Wipes every progress table for one account (used by account deletion). */
    @Transaction
    fun wipe(uid: String) {
        deleteProgress(uid)
        deleteLearnedSigns(uid)
        deleteClaimedDays(uid)
        deleteAchievements(uid)
    }
}

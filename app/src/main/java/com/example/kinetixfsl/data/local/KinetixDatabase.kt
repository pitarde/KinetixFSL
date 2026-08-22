package com.example.kinetixfsl.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's offline-first local database (SQLite, via Room).
 * One database holds every account's data, isolated by a `uid` column on each
 * table (the Firebase account id, or "guest" when signed out), so switching
 * accounts never bleeds one learner's progress into another's.
 */
@Database(
    entities = [
        // Progress domain
        ProgressEntity::class,
        LearnedSignEntity::class,
        ClaimedStreakDayEntity::class,
        UnlockedAchievementEntity::class,
        // Activity-log domain
        ActivityDayEntity::class,
        ActivityHourEntity::class,
        SignLastPracticedEntity::class,
        LessonCountEntity::class,
        CategoryQuizStatEntity::class,
        CameraErrorEntity::class,
        QuizMistakeEntity::class,
        // Quiz-game domain
        QuizProgressEntity::class,
        QuizPassedLevelEntity::class,
        QuizFirstAssignedLevelEntity::class,
        QuizScoreEntity::class,
        QuizUsedInitialPassEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class KinetixDatabase : RoomDatabase() {

    abstract fun progressDao(): ProgressDao
    abstract fun activityDao(): ActivityDao
    abstract fun quizDao(): QuizDao

    companion object {
        @Volatile private var instance: KinetixDatabase? = null

        fun get(context: Context): KinetixDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KinetixDatabase::class.java,
                    "kinetix.db",
                )
                    // The stores are called synchronously from the UI layer
                    // (repositories are read inside composables), so main-thread
                    // queries are permitted; the database is tiny — a handful of
                    // rows per account, written only at event boundaries.
                    .allowMainThreadQueries()
                    .build().also { instance = it }
            }
    }
}

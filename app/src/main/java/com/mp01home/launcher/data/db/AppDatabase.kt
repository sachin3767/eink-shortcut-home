package com.mp01home.launcher.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Single local cache for all integrations (EINK-7: every widget renders from here).
 *
 * Adding a table: create the @Entity + @Dao in its own file in this package, add the entity
 * to [entities], add an abstract DAO getter, and bump [version]. Migrations are destructive
 * (the DB is a cache that re-syncs), so never store the only copy of user data here without
 * accepting that a schema bump wipes it.
 */
@Database(
    entities = [
        CachedEvent::class,
        CachedTask::class,
        PendingTaskOp::class,
        MailNotification::class,
        DigestNotification::class,
        TopPriority::class,
        Habit::class,
        HabitEntry::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cachedEventDao(): CachedEventDao
    abstract fun cachedTaskDao(): CachedTaskDao
    abstract fun pendingTaskOpDao(): PendingTaskOpDao
    abstract fun mailNotificationDao(): MailNotificationDao
    abstract fun digestNotificationDao(): DigestNotificationDao
    abstract fun topPriorityDao(): TopPriorityDao
    abstract fun habitDao(): HabitDao

    companion object {
        const val NAME = "mp01_launcher.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}

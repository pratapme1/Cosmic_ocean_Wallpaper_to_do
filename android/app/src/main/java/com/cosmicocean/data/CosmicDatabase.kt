package com.cosmicocean.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        StarEntity::class,
        ConstellationLinkEntity::class,
        OrbitEntity::class,
        PatinaEntity::class,
        TrophyEntity::class,
        AchievementStatsEntity::class,
        SyncQueueEntity::class  // Local-first sync queue
    ],
    version = 3,  // Bumped for local-first architecture
    exportSchema = false
)
abstract class CosmicDatabase : RoomDatabase() {
    abstract fun starDao(): StarDao
    abstract fun constellationDao(): ConstellationDao
    abstract fun orbitDao(): OrbitDao
    abstract fun trophyDao(): TrophyDao
    abstract fun achievementStatsDao(): AchievementStatsDao
    abstract fun syncQueueDao(): SyncQueueDao  // Local-first sync queue

    companion object {
        @Volatile
        private var INSTANCE: CosmicDatabase? = null

        /**
         * Migration from version 2 to 3:
         * - Add sync fields to stars table (syncStatus, syncVersion, updatedAt, isDeleted)
         * - Create sync_queue table for pending operations
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add sync fields to stars table
                database.execSQL("ALTER TABLE stars ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'synced'")
                database.execSQL("ALTER TABLE stars ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE stars ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
                database.execSQL("ALTER TABLE stars ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")

                // Create sync_queue table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_queue (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        taskId TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        lastError TEXT
                    )
                """)
            }
        }

        fun getDatabase(context: Context): CosmicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CosmicDatabase::class.java,
                    "cosmic_ocean_db"
                )
                    .addMigrations(MIGRATION_2_3)  // Use proper migration for local-first
                    .fallbackToDestructiveMigration()  // Fallback for other schema changes
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

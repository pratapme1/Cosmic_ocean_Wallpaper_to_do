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
        SyncQueueEntity::class
    ],
    version = 6,  // Bump for subtask parentId support
    exportSchema = false
)
abstract class CosmicDatabase : RoomDatabase() {
    abstract fun starDao(): StarDao
    abstract fun constellationDao(): ConstellationDao
    abstract fun orbitDao(): OrbitDao
    abstract fun trophyDao(): TrophyDao
    abstract fun achievementStatsDao(): AchievementStatsDao
    abstract fun syncQueueDao(): SyncQueueDao

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
                database.execSQL("ALTER TABLE stars ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'synced'")
                database.execSQL("ALTER TABLE stars ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE stars ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
                database.execSQL("ALTER TABLE stars ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")

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

        /**
         * CRITICAL FIX: Migration from version 3 to 4
         * - Separate localId from serverId
         * - Add serverUpdatedAt for conflict resolution
         * - Update sync_queue to use localTaskId
         * 
         * WARNING: This migration requires data migration
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // CRITICAL FIX: Migration Issue #8 - Handle existing data properly
                
                // Step 1: Create new stars table with corrected schema
                database.execSQL("""
                    CREATE TABLE stars_new (
                        localId TEXT PRIMARY KEY NOT NULL,
                        serverId TEXT,
                        title TEXT NOT NULL,
                        urgency INTEGER NOT NULL,
                        dueDate INTEGER,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        createdAt INTEGER NOT NULL,
                        isSubtask INTEGER NOT NULL DEFAULT 0,
                        isRecurring INTEGER NOT NULL DEFAULT 0,
                        echoInterval TEXT,
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        completedAt INTEGER,
                        isArchived INTEGER NOT NULL DEFAULT 0,
                        archivedAt INTEGER,
                        syncStatus TEXT NOT NULL DEFAULT 'pending',
                        syncVersion INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL DEFAULT 0,
                        serverUpdatedAt INTEGER
                    )
                """)
                
                // Step 2: Migrate existing data
                // CRITICAL FIX: Set all existing as pending sync to ensure consistency
                database.execSQL("""
                    INSERT INTO stars_new (
                        localId, serverId, title, urgency, dueDate, x, y, createdAt,
                        isSubtask, isRecurring, echoInterval, isCompleted, completedAt,
                        isArchived, archivedAt, syncStatus, syncVersion, updatedAt, isDeleted
                    )
                    SELECT 
                        id as localId,
                        CASE 
                            WHEN syncStatus = 'synced' THEN id  -- If synced, id is server id
                            ELSE NULL  -- If not synced, no server id yet
                        END as serverId,
                        title, urgency, dueDate, x, y, createdAt,
                        isSubtask, isRecurring, echoInterval, isCompleted, completedAt,
                        isArchived, archivedAt,
                        CASE 
                            WHEN syncStatus = 'synced' THEN 'pending'  -- CRITICAL FIX: Force re-sync
                            ELSE syncStatus
                        END as syncStatus,
                        syncVersion, updatedAt, isDeleted
                    FROM stars
                """)
                
                // Step 3: Drop old table
                database.execSQL("DROP TABLE stars")
                
                // Step 4: Rename new table
                database.execSQL("ALTER TABLE stars_new RENAME TO stars")
                
                // Step 5: Recreate sync_queue with localTaskId
                database.execSQL("""
                    CREATE TABLE sync_queue_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        localTaskId TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        lastError TEXT,
                        clientTimestamp INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}
                    )
                """)
                
                // Step 6: Migrate sync queue data
                database.execSQL("""
                    INSERT INTO sync_queue_new (id, localTaskId, operation, payload, createdAt, retryCount, lastError)
                    SELECT id, taskId as localTaskId, operation, payload, createdAt, retryCount, lastError
                    FROM sync_queue
                """)
                
                // Step 7: Drop old sync_queue
                database.execSQL("DROP TABLE sync_queue")
                
                // Step 8: Rename new sync_queue
                database.execSQL("ALTER TABLE sync_queue_new RENAME TO sync_queue")
                
                // Step 9: Create indexes for performance
                database.execSQL("CREATE INDEX idx_stars_server_id ON stars(serverId)")
                database.execSQL("CREATE INDEX idx_stars_sync_status ON stars(syncStatus)")
                database.execSQL("CREATE INDEX idx_sync_queue_local_task_id ON sync_queue(localTaskId)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE stars ADD COLUMN contextTag TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE stars ADD COLUMN parentId TEXT")
            }
        }

        fun getDatabase(context: Context): CosmicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CosmicDatabase::class.java,
                    "cosmic_ocean_db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

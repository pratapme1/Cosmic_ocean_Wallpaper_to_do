package com.cosmicocean.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        StarEntity::class,
        ConstellationLinkEntity::class,
        OrbitEntity::class,
        PatinaEntity::class,
        TrophyEntity::class,
        AchievementStatsEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class CosmicDatabase : RoomDatabase() {
    abstract fun starDao(): StarDao
    abstract fun constellationDao(): ConstellationDao
    abstract fun orbitDao(): OrbitDao
    abstract fun trophyDao(): TrophyDao
    abstract fun achievementStatsDao(): AchievementStatsDao

    companion object {
        @Volatile
        private var INSTANCE: CosmicDatabase? = null

        fun getDatabase(context: Context): CosmicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CosmicDatabase::class.java,
                    "cosmic_ocean_db"
                )
                    .fallbackToDestructiveMigration() // For development - recreate on schema change
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

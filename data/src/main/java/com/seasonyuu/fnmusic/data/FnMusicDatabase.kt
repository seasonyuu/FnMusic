package com.seasonyuu.fnmusic.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playback_queue")
data class PlaybackQueueEntity(
    @PrimaryKey val trackGuid: String,
    val queueIndex: Int,
    val positionMs: Long = 0,
    val isCurrent: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: String = "Off",
    val trackJson: String? = null,
    val isRoaming: Boolean = false,
    val roamId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface PlaybackQueueDao {
    @Query("SELECT * FROM playback_queue ORDER BY queueIndex")
    fun observe(): Flow<List<PlaybackQueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<PlaybackQueueEntity>)

    @Query("DELETE FROM playback_queue")
    suspend fun clear()

    @Transaction
    suspend fun replace(items: List<PlaybackQueueEntity>) {
        clear()
        insert(items)
    }
}

@Database(entities = [PlaybackQueueEntity::class], version = 3, exportSchema = false)
abstract class FnMusicDatabase : RoomDatabase() {
    abstract fun playbackQueue(): PlaybackQueueDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playback_queue ADD COLUMN trackJson TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playback_queue ADD COLUMN isRoaming INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE playback_queue ADD COLUMN roamId TEXT")
            }
        }
    }
}

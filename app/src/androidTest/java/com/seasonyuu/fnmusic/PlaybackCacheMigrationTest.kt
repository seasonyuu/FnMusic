package com.seasonyuu.fnmusic

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seasonyuu.fnmusic.data.FnMusicDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackCacheMigrationTest {
    @Test fun upgradesVersionOneWithoutLosingQueueOrPosition() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "playback-migration-test.db"
        context.deleteDatabase(name)
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
                old.execSQL("""CREATE TABLE playback_queue (
                    trackGuid TEXT NOT NULL PRIMARY KEY, queueIndex INTEGER NOT NULL,
                    positionMs INTEGER NOT NULL, isCurrent INTEGER NOT NULL,
                    shuffleEnabled INTEGER NOT NULL, repeatMode TEXT NOT NULL, updatedAt INTEGER NOT NULL
                )""")
                old.execSQL("INSERT INTO playback_queue VALUES ('saved-song', 0, 4321, 1, 1, 'All', 123)")
                old.version = 1
            }
            val db = Room.databaseBuilder(context, FnMusicDatabase::class.java, name)
                .addMigrations(FnMusicDatabase.MIGRATION_1_2).build()
            try {
                val row = db.playbackQueue().observe().first().single()
                assertEquals("saved-song", row.trackGuid)
                assertEquals(4321L, row.positionMs)
                assertTrue(row.isCurrent)
                assertTrue(row.shuffleEnabled)
                assertEquals("All", row.repeatMode)
                assertNull(row.trackJson)
                db.playbackQueue().replace(listOf(row.copy(trackJson = "cached metadata")))
                assertEquals("cached metadata", db.playbackQueue().observe().first().single().trackJson)
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}

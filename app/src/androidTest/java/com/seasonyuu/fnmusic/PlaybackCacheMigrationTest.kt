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
    @Test fun upgradesVersionThreeAndPersistsLyricsChoiceAfterReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "lyrics-migration-test.db"
        context.deleteDatabase(name)
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
                old.execSQL("""CREATE TABLE playback_queue (
                    trackGuid TEXT NOT NULL PRIMARY KEY, queueIndex INTEGER NOT NULL,
                    positionMs INTEGER NOT NULL, isCurrent INTEGER NOT NULL,
                    shuffleEnabled INTEGER NOT NULL, repeatMode TEXT NOT NULL, updatedAt INTEGER NOT NULL,
                    trackJson TEXT, isRoaming INTEGER NOT NULL DEFAULT 0, roamId TEXT
                )""")
                old.execSQL("INSERT INTO playback_queue VALUES ('saved-song', 0, 5432, 1, 0, 'Off', 123, 'metadata', 1, 'roam')")
                old.version = 3
            }
            val choice = com.seasonyuu.fnmusic.data.LyricsChoiceEntity("nas|account", "saved-song", "{\"mode\":\"FnMusic\",\"offsetMs\":100}")
            Room.databaseBuilder(context, FnMusicDatabase::class.java, name).addMigrations(FnMusicDatabase.MIGRATION_3_4).build().let { db ->
                try {
                    val row = db.playbackQueue().observe().first().single()
                    assertEquals(5432L, row.positionMs)
                    assertEquals("metadata", row.trackJson)
                    assertTrue(row.isRoaming)
                    assertEquals("roam", row.roamId)
                    db.lyricsChoices().save(choice)
                } finally { db.close() }
            }
            Room.databaseBuilder(context, FnMusicDatabase::class.java, name).build().let { db ->
                try { assertEquals(choice, db.lyricsChoices().observe("nas|account", "saved-song").first()) }
                finally { db.close() }
            }
        } finally { context.deleteDatabase(name) }
    }

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
                .addMigrations(FnMusicDatabase.MIGRATION_1_2, FnMusicDatabase.MIGRATION_2_3, FnMusicDatabase.MIGRATION_3_4).build()
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
                val choice = com.seasonyuu.fnmusic.data.LyricsChoiceEntity("nas|account", "saved-song", "{\"mode\":\"FnMusic\"}")
                db.lyricsChoices().save(choice)
                assertEquals(choice, db.lyricsChoices().observe("nas|account", "saved-song").first())
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}

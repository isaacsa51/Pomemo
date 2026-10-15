package com.serranoie.app.pomemo

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serranoie.app.pomemo.data.AppDatabase
import com.serranoie.app.pomemo.data.MIGRATION_1_2
import com.serranoie.app.pomemo.data.MIGRATION_2_3
import com.serranoie.app.pomemo.data.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** A v1 install (reminders only) upgrades through v2 and v3 without losing reminders, and notes work afterwards. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @Test fun v1RemindersSurviveUpgrade() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-test.db"
        context.deleteDatabase(name)

        // The v1 schema exactly as Room created it before notes existed.
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `Reminder` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `note` TEXT NOT NULL, `time` INTEGER NOT NULL, `days` INTEGER NOT NULL, `date` INTEGER, `ring` INTEGER NOT NULL, `enabled` INTEGER NOT NULL)")
            db.execSQL("INSERT INTO Reminder (title, note, time, days, date, ring, enabled) VALUES ('Pastilla', '', 30600, 127, NULL, 1, 1)")
            db.version = 1
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        try {
            val reminders = room.reminderDao().observeAll().first()
            assertEquals(listOf("Pastilla"), reminders.map { it.title })
            assertEquals(null, reminders.single().skipDate)
            room.noteDao().upsert(Note(body = "# hola", updatedAt = 1))
            assertEquals(1, room.noteDao().observeAll().first().size)
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }
}

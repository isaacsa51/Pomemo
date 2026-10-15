package com.serranoie.app.pomemo.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate
import java.time.LocalTime

@Database(entities = [Reminder::class, Note::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun noteDao(): NoteDao
}

/** v2 adds notes. Written by hand so existing reminders survive the upgrade (no destructive fallback). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `Note` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `body` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)")
    }
}

/** v3: "skip today" and an attached note on reminders. Both nullable, so old rows need nothing. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Reminder` ADD COLUMN `skipDate` INTEGER")
        db.execSQL("ALTER TABLE `Reminder` ADD COLUMN `noteId` INTEGER")
    }
}

class Converters {
    @TypeConverter fun timeToInt(t: LocalTime): Int = t.toSecondOfDay()
    @TypeConverter fun intToTime(s: Int): LocalTime = LocalTime.ofSecondOfDay(s.toLong())
    @TypeConverter fun dateToLong(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun longToDate(d: Long?): LocalDate? = d?.let(LocalDate::ofEpochDay)
}

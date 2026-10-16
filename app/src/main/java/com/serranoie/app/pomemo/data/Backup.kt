package com.serranoie.app.pomemo.data

import androidx.room.withTransaction
import com.serranoie.app.pomemo.alarm.AlarmScheduler
import com.serranoie.app.pomemo.reminders.nextTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

data class BackupCounts(val notes: Int, val reminders: Int)

class NotABackupException : Exception()

/**
 * Notes and reminders to and from one JSON file the user keeps wherever they like. Import merges:
 * nothing is deleted, exact duplicates are skipped, and reminders keep pointing at their notes
 * (ids are remapped). Imported reminders are scheduled right away.
 */
@Singleton
class Backup @Inject constructor(private val db: AppDatabase, private val scheduler: AlarmScheduler) {
    private val notes = db.noteDao()
    private val reminders = db.reminderDao()

    suspend fun export(out: OutputStream): BackupCounts = withContext(Dispatchers.IO) {
        val n = notes.getAll()
        val r = reminders.getAll()
        val json = JSONObject()
            .put("app", APP)
            .put("version", VERSION)
            .put("notes", JSONArray(n.map { it.toJson() }))
            .put("reminders", JSONArray(r.map { it.toJson() }))
        out.bufferedWriter().use { it.write(json.toString(2)) }
        BackupCounts(n.size, r.size)
    }

    suspend fun import(input: InputStream): BackupCounts = withContext(Dispatchers.IO) {
        val json = runCatching { JSONObject(input.bufferedReader().use { it.readText() }) }.getOrNull()
        if (json == null || json.optString("app") != APP || json.optInt("version") > VERSION) throw NotABackupException()
        val now = LocalDateTime.now()
        var addedNotes = 0
        var addedReminders = 0
        val toSchedule = mutableListOf<Reminder>()
        db.withTransaction {
            // Old note id → id in this database, so attached notes still match up.
            val ids = mutableMapOf<Long, Long>()
            val existing = notes.getAll().associateBy { it.body }
            json.optJSONArray("notes")?.objects()?.forEach { o ->
                val note = o.toNote()
                ids[note.id] = existing[note.body]?.id ?: notes.upsert(note.copy(id = 0)).also { addedNotes++ }
            }
            val have = reminders.getAll().map { it.identity() }.toSet()
            json.optJSONArray("reminders")?.objects()?.forEach { o ->
                var r = o.toReminder().let { it.copy(id = 0, noteId = it.noteId?.let(ids::get)) }
                if (r.identity() in have) return@forEach
                // A one-shot that already went by would sit "on" forever; bring it in switched off.
                if (r.days == 0 && nextTrigger(r, now) == null) r = r.copy(enabled = false)
                toSchedule += r.copy(id = reminders.upsert(r))
                addedReminders++
            }
        }
        toSchedule.forEach(scheduler::schedule)
        BackupCounts(addedNotes, addedReminders)
    }

    private fun Reminder.identity() = listOf(title, time, days, date)

    private fun Note.toJson() = JSONObject().put("id", id).put("body", body).put("updatedAt", updatedAt)

    private fun JSONObject.toNote() = Note(getLong("id"), getString("body"), optLong("updatedAt", System.currentTimeMillis()))

    private fun Reminder.toJson() = JSONObject()
        .put("id", id).put("title", title).put("note", note)
        .put("time", time.toString()).put("days", days).put("date", date?.toString())
        .put("ring", ring).put("enabled", enabled)
        .put("skipDate", skipDate?.toString()).put("noteId", noteId)

    private fun JSONObject.toReminder() = Reminder(
        id = optLong("id"),
        title = getString("title"),
        note = optString("note"),
        time = LocalTime.parse(getString("time")),
        days = optInt("days"),
        date = optDate("date"),
        ring = optBoolean("ring", true),
        enabled = optBoolean("enabled", true),
        skipDate = optDate("skipDate"),
        noteId = if (isNull("noteId")) null else getLong("noteId"),
    )

    private fun JSONObject.optDate(key: String) = if (isNull(key)) null else LocalDate.parse(getString(key))

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private companion object {
        const val APP = "pomemo"
        const val VERSION = 1
    }
}

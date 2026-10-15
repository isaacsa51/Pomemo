package com.serranoie.app.pomemo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

@Entity
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val time: LocalTime,
    /** Bitmask, Monday = 1 shl 0 … Sunday = 1 shl 6. 0 = one-shot on [date]. */
    val days: Int = 0,
    /** One-shot date, or first day for recurring reminders. Null = today. */
    val date: LocalDate? = null,
    /** true = full-screen alarm, false = plain notification. */
    val ring: Boolean = true,
    val enabled: Boolean = true,
    /** "Skip today": the one date this recurring reminder stays quiet. A past date does nothing. */
    val skipDate: LocalDate? = null,
    /** Note from the Notes tab shown (checklist and all) on the alarm screen. */
    val noteId: Long? = null,
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM Reminder ORDER BY time")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM Reminder WHERE id = :id")
    suspend fun get(id: Long): Reminder?

    @Query("SELECT * FROM Reminder WHERE enabled = 1")
    suspend fun getEnabled(): List<Reminder>

    @Upsert
    suspend fun upsert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)
}

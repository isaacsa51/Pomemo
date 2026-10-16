package com.serranoie.app.pomemo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A markdown note. The first line doubles as its title. */
@Entity
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val body: String,
    /** Epoch millis of the last save; newest notes list first. */
    val updatedAt: Long,
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM Note ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM Note WHERE id = :id")
    suspend fun get(id: Long): Note?

    @Query("SELECT * FROM Note")
    suspend fun getAll(): List<Note>

    @Query("SELECT * FROM Note WHERE id = :id")
    fun observe(id: Long): Flow<Note?>

    @Upsert
    suspend fun upsert(note: Note): Long

    @Delete
    suspend fun delete(note: Note)
}

package com.serranoie.app.pomemo.di

import android.content.Context
import androidx.room.Room
import com.serranoie.app.pomemo.data.AppDatabase
import com.serranoie.app.pomemo.data.MIGRATION_1_2
import com.serranoie.app.pomemo.data.MIGRATION_2_3
import com.serranoie.app.pomemo.data.NoteDao
import com.serranoie.app.pomemo.data.ReminderDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "pomemo.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

    @Provides
    fun reminderDao(db: AppDatabase): ReminderDao = db.reminderDao()

    @Provides
    fun noteDao(db: AppDatabase): NoteDao = db.noteDao()
}

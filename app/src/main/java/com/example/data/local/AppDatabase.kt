package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DocumentEntity::class,
        PageEntity::class,
        SearchTokenEntity::class,
        QuizEntity::class,
        QuizQuestionEntity::class,
        MistakeEntity::class,
        ModelFileEntity::class,
        StudyActivityEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun pageDao(): PageDao
    abstract fun searchTokenDao(): SearchTokenDao
    abstract fun quizDao(): QuizDao
    abstract fun mistakeDao(): MistakeDao
    abstract fun modelFileDao(): ModelFileDao
    abstract fun studyActivityDao(): StudyActivityDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "thanaweya_offline.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

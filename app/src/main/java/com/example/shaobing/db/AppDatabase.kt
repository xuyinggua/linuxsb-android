package com.example.shaobing.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Bookmark::class,
        Userscript::class,
        UserProfile::class,
        ProfileSnapshot::class,
        GmValue::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun userscriptDao(): UserscriptDao
    abstract fun profileDao(): ProfileDao
    abstract fun profileSnapshotDao(): ProfileSnapshotDao
    abstract fun gmValueDao(): GmValueDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN username TEXT")
                db.execSQL("ALTER TABLE profiles ADD COLUMN uid TEXT")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shaobing.db"
                ).allowMainThreadQueries()
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
    }
}

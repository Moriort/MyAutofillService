package com.example.myautofillservice.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Credential::class, WebSite::class],
    version = 2,
    exportSchema = false
)
abstract class CredentialDatabase : RoomDatabase() {
    
    abstract fun credentialDao(): CredentialDao
    abstract fun webSiteDao(): WebSiteDao
    
    companion object {
        @Volatile
        private var INSTANCE: CredentialDatabase? = null
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `websites` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `pageId` TEXT NOT NULL,
                        `url` TEXT,
                        `domain` TEXT,
                        `signature` TEXT NOT NULL,
                        `isUserNamed` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `lastUsed` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
        
        fun getDatabase(context: Context): CredentialDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CredentialDatabase::class.java,
                    "credential_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
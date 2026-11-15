package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PeerEntity::class, MessageEntity::class, CallLogEntity::class],
    version = 2,
    exportSchema = false
)
abstract class HisnDatabase : RoomDatabase() {
    abstract fun peerDao(): PeerDao
    abstract fun messageDao(): MessageDao
    abstract fun callLogDao(): CallLogDao

    companion object {
        @Volatile
        private var INSTANCE: HisnDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE peers ADD COLUMN userCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE peers ADD COLUMN isBusyInCall INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): HisnDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HisnDatabase::class.java,
                    "hisn_encrypted_lan.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

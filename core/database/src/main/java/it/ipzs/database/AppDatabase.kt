package it.ipzs.database

import androidx.room.Database
import androidx.room.RoomDatabase
import it.ipzs.database.daos.LogDao
import it.ipzs.database.entities.LogDb

@Database(version = 1, entities = [LogDb::class])
abstract class AppDatabase: RoomDatabase() {

    abstract fun logDao(): LogDao

}
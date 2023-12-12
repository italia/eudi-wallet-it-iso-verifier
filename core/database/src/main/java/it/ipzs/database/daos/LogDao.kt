package it.ipzs.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import it.ipzs.database.entities.LogDb
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {

    @Insert
    suspend fun insertLog(logDb: LogDb): Long

    @Query("select * from logs order by timestamp desc")
    fun getLogs(): Flow<List<LogDb>>

    @Query("select * from logs where timestamp = :id")
    suspend fun getById(id: Long): LogDb

    @Query("delete from logs where timestamp = :id")
    suspend fun deleteItem(id: Long): Int

}
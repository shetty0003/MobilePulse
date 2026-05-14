package com.mobilepulse.app.data.db.dao

import androidx.room.*
import com.mobilepulse.app.data.db.entity.ActivityLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM activity_log ORDER BY timestamp DESC LIMIT 200")
    fun getAllLogs(): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_log WHERE type = :type ORDER BY timestamp DESC")
    fun getLogsByType(type: String): Flow<List<ActivityLogEntity>>

    @Insert
    suspend fun insertLog(log: ActivityLogEntity)

    @Query("DELETE FROM activity_log WHERE id NOT IN (SELECT id FROM activity_log ORDER BY timestamp DESC LIMIT 200)")
    suspend fun pruneToLimit()

    @Query("DELETE FROM activity_log")
    suspend fun clearAll()
}

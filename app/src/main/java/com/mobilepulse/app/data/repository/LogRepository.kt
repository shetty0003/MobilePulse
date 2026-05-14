package com.mobilepulse.app.data.repository

import com.mobilepulse.app.data.db.dao.LogDao
import com.mobilepulse.app.data.db.entity.ActivityLogEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepository @Inject constructor(
    private val logDao: LogDao
) {
    fun getAllLogs(): Flow<List<ActivityLogEntity>> = logDao.getAllLogs()

    fun getLogsByType(type: String): Flow<List<ActivityLogEntity>> =
        logDao.getLogsByType(type)

    /** Primary log function used by most callers. */
    suspend fun log(
        type: String,
        message: String,
        affectedApp: String? = null
    ) {
        logDao.insertLog(ActivityLogEntity(type = type, message = message, affectedApp = affectedApp))
        logDao.pruneToLimit()
    }

    /**
     * Alias used by AutoOptimizeWorker.
     * Delegates to log() so both callers work without duplication.
     */
    suspend fun insert(
        type: String,
        message: String,
        affectedApp: String? = null
    ) = log(type, message, affectedApp)

    suspend fun clearAll() = logDao.clearAll()
}
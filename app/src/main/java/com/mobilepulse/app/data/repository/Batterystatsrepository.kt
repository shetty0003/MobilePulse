package com.mobilepulse.app.data.repository

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ── Entity ────────────────────────────────────────────────────────────────────

@Entity(tableName = "battery_samples")
data class BatterySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val levelPercent: Int,
    val isCharging: Boolean,
    val temperatureCelsius: Float
)

// ── DAO ───────────────────────────────────────────────────────────────────────

@Dao
interface BatterySampleDao {
    @Insert
    suspend fun insert(sample: BatterySampleEntity)

    @Query("SELECT * FROM battery_samples WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun samplesAfter(since: Long): Flow<List<BatterySampleEntity>>

    @Query("SELECT * FROM battery_samples ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): BatterySampleEntity?

    @Query("DELETE FROM battery_samples WHERE timestamp < :cutoff")
    suspend fun pruneBefore(cutoff: Long)

    @Query("SELECT COUNT(*) FROM battery_samples")
    suspend fun count(): Int
}

// ── Repository ────────────────────────────────────────────────────────────────

@Singleton
class BatteryStatsRepository @Inject constructor(
    private val dao: BatterySampleDao
) {
    /** Record a new sample. Called from MonitoringService on each tick. */
    suspend fun record(
        levelPercent: Int,
        isCharging: Boolean,
        temperatureCelsius: Float
    ) = withContext(Dispatchers.IO) {
        dao.insert(
            BatterySampleEntity(
                timestamp          = System.currentTimeMillis(),
                levelPercent       = levelPercent,
                isCharging         = isCharging,
                temperatureCelsius = temperatureCelsius
            )
        )
    }

    /** Samples from the last [hours] hours as a Flow. */
    fun samplesForLast(hours: Int = 24): Flow<List<BatterySampleEntity>> {
        val since = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(hours.toLong())
        return dao.samplesAfter(since)
    }

    /** Latest single sample — useful for quick reads. */
    suspend fun getLatest(): BatterySampleEntity? = dao.getLatest()

    /** Prune entries older than 7 days. Call from a periodic worker. */
    suspend fun pruneOld() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        dao.pruneBefore(cutoff)
    }
}
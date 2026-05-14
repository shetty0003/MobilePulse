package com.mobilepulse.app.data.db.dao

import androidx.room.*
import com.mobilepulse.app.data.db.entity.IgnoreListEntity
import com.mobilepulse.app.data.db.entity.WhitelistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExemptionDao {
    // Whitelist
    @Query("SELECT * FROM whitelist ORDER BY addedAt DESC")
    fun getWhitelist(): Flow<List<WhitelistEntity>>

    @Query("SELECT COUNT(*) FROM whitelist WHERE packageName = :pkg")
    suspend fun isWhitelisted(pkg: String): Int

    @Query("SELECT packageName FROM whitelist")
    suspend fun getAllWhitelistedPackages(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToWhitelist(entry: WhitelistEntity)

    @Query("DELETE FROM whitelist WHERE packageName = :pkg")
    suspend fun removeFromWhitelist(pkg: String)

    // Ignore list
    @Query("SELECT * FROM ignore_list ORDER BY addedAt DESC")
    fun getIgnoreList(): Flow<List<IgnoreListEntity>>

    @Query("SELECT COUNT(*) FROM ignore_list WHERE packageName = :pkg")
    suspend fun isIgnored(pkg: String): Int

    @Query("SELECT packageName FROM ignore_list")
    suspend fun getAllIgnoredPackages(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToIgnoreList(entry: IgnoreListEntity)

    @Query("DELETE FROM ignore_list WHERE packageName = :pkg")
    suspend fun removeFromIgnoreList(pkg: String)
}
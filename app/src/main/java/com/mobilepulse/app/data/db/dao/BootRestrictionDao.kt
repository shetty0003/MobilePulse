package com.mobilepulse.app.data.db.dao

import androidx.room.*
import com.mobilepulse.app.data.db.entity.BootRestrictionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BootRestrictionDao {

    @Query("SELECT * FROM boot_restrictions ORDER BY appName ASC")
    fun getAll(): Flow<List<BootRestrictionEntity>>

    @Query("SELECT * FROM boot_restrictions")
    suspend fun getAllList(): List<BootRestrictionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BootRestrictionEntity)

    @Query("UPDATE boot_restrictions SET restrictBackground = :value WHERE packageName = :pkg")
    suspend fun setRestrictBackground(pkg: String, value: Boolean)

    @Query("DELETE FROM boot_restrictions WHERE packageName = :pkg")
    suspend fun deleteByPackage(pkg: String)
}

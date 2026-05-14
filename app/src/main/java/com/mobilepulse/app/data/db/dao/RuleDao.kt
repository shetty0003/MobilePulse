package com.mobilepulse.app.data.db.dao

import androidx.room.*
import com.mobilepulse.app.data.db.entity.AutomationRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM automation_rules ORDER BY createdAt DESC")
    fun getAllRules(): Flow<List<AutomationRuleEntity>>

    @Query("SELECT * FROM automation_rules WHERE isEnabled = 1")
    suspend fun getActiveRules(): List<AutomationRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AutomationRuleEntity)

    @Update
    suspend fun updateRule(rule: AutomationRuleEntity)

    @Delete
    suspend fun deleteRule(rule: AutomationRuleEntity)

    @Query("UPDATE automation_rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setRuleEnabled(id: Int, enabled: Boolean)
}

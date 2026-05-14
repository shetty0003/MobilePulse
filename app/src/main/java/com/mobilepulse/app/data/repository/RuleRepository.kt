package com.mobilepulse.app.data.repository

import com.mobilepulse.app.data.db.dao.RuleDao
import com.mobilepulse.app.data.db.entity.AutomationRuleEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleRepository @Inject constructor(
    private val ruleDao: RuleDao
) {
    fun getAllRules(): Flow<List<AutomationRuleEntity>> = ruleDao.getAllRules()

    suspend fun getActiveRules(): List<AutomationRuleEntity> = ruleDao.getActiveRules()

    suspend fun insertRule(rule: AutomationRuleEntity) = ruleDao.insertRule(rule)

    suspend fun updateRule(rule: AutomationRuleEntity) = ruleDao.updateRule(rule)

    suspend fun deleteRule(rule: AutomationRuleEntity) = ruleDao.deleteRule(rule)

    suspend fun setRuleEnabled(id: Int, enabled: Boolean) =
        ruleDao.setRuleEnabled(id, enabled)
}

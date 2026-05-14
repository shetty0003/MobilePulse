package com.mobilepulse.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilepulse.app.data.db.entity.AutomationRuleEntity
import com.mobilepulse.app.data.repository.RuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutomationViewModel @Inject constructor(
    private val ruleRepo: RuleRepository
) : ViewModel() {

    val rules = ruleRepo.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun insertRule(rule: AutomationRuleEntity) = viewModelScope.launch {
        ruleRepo.insertRule(rule)
    }

    fun deleteRule(rule: AutomationRuleEntity) = viewModelScope.launch {
        ruleRepo.deleteRule(rule)
    }

    fun toggleRule(id: Int, enabled: Boolean) = viewModelScope.launch {
        ruleRepo.setRuleEnabled(id, enabled)
    }
}

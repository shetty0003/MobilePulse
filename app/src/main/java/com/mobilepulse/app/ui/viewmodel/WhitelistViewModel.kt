package com.mobilepulse.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilepulse.app.data.repository.ExemptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WhitelistViewModel @Inject constructor(
    private val exemptionRepo: ExemptionRepository
) : ViewModel() {

    val whitelist = exemptionRepo.getWhitelist()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ignoreList = exemptionRepo.getIgnoreList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addToWhitelist(pkg: String, name: String) = viewModelScope.launch {
        exemptionRepo.addToWhitelist(pkg, name)
    }
    fun removeFromWhitelist(pkg: String) = viewModelScope.launch {
        exemptionRepo.removeFromWhitelist(pkg)
    }
    fun addToIgnoreList(pkg: String, name: String) = viewModelScope.launch {
        exemptionRepo.addToIgnoreList(pkg, name)
    }
    fun removeFromIgnoreList(pkg: String) = viewModelScope.launch {
        exemptionRepo.removeFromIgnoreList(pkg)
    }
}


package com.mobilepulse.app.ui.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilepulse.app.data.db.entity.BootRestrictionEntity
import com.mobilepulse.app.data.repository.BootRestrictionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InstalledAppInfo(val packageName: String, val appName: String)

@HiltViewModel
class BootRestrictionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: BootRestrictionRepository
) : ViewModel() {

    val restrictions: StateFlow<List<BootRestrictionEntity>> = repo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    fun loadInstalledApps() = viewModelScope.launch(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }
        _installedApps.value = packages
            .filter { info ->
                (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                        info.packageName != context.packageName
            }
            .map { InstalledAppInfo(it.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.appName }
    }

    fun addRestriction(packageName: String, appName: String) = viewModelScope.launch {
        repo.add(BootRestrictionEntity(packageName = packageName, appName = appName))
    }

    fun setRestrictBackground(packageName: String, value: Boolean) = viewModelScope.launch {
        repo.setRestrictBackground(packageName, value)
    }

    fun removeRestriction(packageName: String) = viewModelScope.launch {
        repo.remove(packageName)
    }
}

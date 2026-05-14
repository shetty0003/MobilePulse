package com.mobilepulse.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.mobilepulse.app.ui.theme.AppTheme
import com.mobilepulse.app.ui.theme.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themeManager: ThemeManager
) : ViewModel() {
    val theme: Flow<AppTheme> = themeManager.themeFlow
}
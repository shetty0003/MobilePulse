package com.mobilepulse.app.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "mp_theme")

@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val KEY_THEME = stringPreferencesKey("app_theme")

    val themeFlow: Flow<AppTheme> = context.themeDataStore.data
        .map { prefs ->
            try {
                AppTheme.valueOf(prefs[KEY_THEME] ?: AppTheme.FOREST.name)
            } catch (e: IllegalArgumentException) {
                AppTheme.FOREST
            }
        }

    suspend fun setTheme(theme: AppTheme) {
        context.themeDataStore.edit { prefs ->
            prefs[KEY_THEME] = theme.name
        }
    }
}
package com.example.obsidiankeep.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val gridColumns: Flow<Int> = context.dataStore.data.map { it[KEY_GRID_COLUMNS] ?: 2 }

    val language: Flow<String> = context.dataStore.data.map { it[KEY_LANGUAGE] ?: LanguageHelper.DEFAULT_LANG }

    suspend fun setGridColumns(columns: Int) {
        context.dataStore.edit { it[KEY_GRID_COLUMNS] = columns.coerceIn(1, 4) }
    }

    /**
     * Сохранить выбранный язык.
     *
     * Запись идёт в два места:
     * 1) SharedPreferences через [LanguageHelper] — синхронно, чтобы
     *    [LanguageHelper.wrap] уже на следующем запуске Activity подхватил
     *    новое значение в attachBaseContext (пока DataStore-флоу прогреется).
     * 2) DataStore — для Compose-подписчиков (SettingsScreen читает флоу).
     */
    suspend fun setLanguage(lang: String) {
        val normalized = if (lang == "en") "en" else "ru"
        LanguageHelper.setLanguage(context, normalized)
        context.dataStore.edit { it[KEY_LANGUAGE] = normalized }
    }

    companion object {
        private val KEY_GRID_COLUMNS = intPreferencesKey("grid_columns")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
    }
}

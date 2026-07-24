package com.example.obsidiankeep.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Синхронное хранилище выбранного языка на базе SharedPreferences.
 *
 * Используется в [android.app.Application.attachBaseContext] и
 * [android.app.Activity.attachBaseContext], чтобы применить выбранный
 * язык ДО создания UI — тогда строки из ресурсов сразу подхватываются
 * с правильной локалью, и язык сохраняется между запусками приложения.
 *
 * DataStore (см. [SettingsManager]) остаётся источником истины для
 * Compose-флоу, а этот класс — быстрое синхронное зеркало для startup-фазы.
 */
object LanguageHelper {

    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANG = "language"
    const val DEFAULT_LANG = "ru"

    /** Синхронно прочитать сохранённый язык ("ru" или "en"). */
    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LANG, DEFAULT_LANG) ?: DEFAULT_LANG
        return if (raw == "en") "en" else "ru"
    }

    /** Синхронно сохранить язык. */
    fun setLanguage(context: Context, lang: String) {
        val normalized = if (lang == "en") "en" else "ru"
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, normalized)
            .apply()
    }

    /**
     * Обернуть [context] конфигурацией с нужной локалью.
     * Используется в attachBaseContext: super.attachBaseContext(LanguageHelper.wrap(base)).
     */
    fun wrap(context: Context): Context {
        val lang = getLanguage(context)
        val locale = Locale(lang)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        // Для корректного RTL/LTR-направления (на будущее, если добавятся арабский/иврит).
        config.setLayoutDirection(locale)

        return context.createConfigurationContext(config)
    }
}

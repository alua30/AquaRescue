package com.aquarescue.app.presentation

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.aquarescue.app.R

/**
 * Языки интерфейса. Переключение — через per-app language API:
 * на Android 13+ язык хранит система (LocaleManager), на Android 8–12 —
 * AppCompat (сервис AppLocalesMetadataHolderService с autoStoreLocales).
 * Список языков для системных настроек — res/xml/locales_config.xml.
 */
enum class AppLanguage(val tag: String, @param:StringRes val label: Int) {
    RUSSIAN("ru", R.string.language_russian),
    KAZAKH("kk", R.string.language_kazakh),
    ENGLISH("en", R.string.language_english);

    companion object {
        val DEFAULT = RUSSIAN

        /** Выбранный язык; если не выбран — русский (язык по умолчанию). */
        fun current(): AppLanguage {
            val locales = AppCompatDelegate.getApplicationLocales()
            val tag = if (locales.isEmpty) null else locales[0]?.language
            return entries.firstOrNull { it.tag == tag } ?: DEFAULT
        }

        /** Есть ли явно выбранный язык приложения. */
        fun isSelected(): Boolean = !AppCompatDelegate.getApplicationLocales().isEmpty

        /**
         * Применяет язык сразу: AppCompat пересоздаёт Activity с новой конфигурацией,
         * процесс и опрос ESP32 продолжают работать. Выбор сохраняется между запусками.
         */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }
    }
}

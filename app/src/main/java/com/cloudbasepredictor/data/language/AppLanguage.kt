package com.cloudbasepredictor.data.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Languages the user can pick in Settings.
 *
 * [SYSTEM] follows the device locale; every other entry overrides it through the
 * per-app locale APIs ([AppCompatDelegate.setApplicationLocales]), which persist the
 * choice across restarts (via the framework on API 33+, via AppCompat's
 * `autoStoreLocales` service on older versions).
 *
 * [endonym] is the language's own name, shown verbatim regardless of the current UI
 * language so users can always recognise their language. Only [SYSTEM] is labelled
 * from a localized string resource.
 */
enum class AppLanguage(
    /** BCP-47 tag applied as the per-app locale, or `null` to follow the system. */
    val languageTag: String?,
    /** The language written in itself, or `null` for [SYSTEM]. */
    val endonym: String?,
) {
    SYSTEM(null, null),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    GERMAN("de", "Deutsch"),
    RUSSIAN("ru", "Русский"),
    CHINESE_SIMPLIFIED("zh-CN", "中文（简体）"),
    ;

    companion object {
        /** The language currently applied, derived from the active per-app locale list. */
        fun current(): AppLanguage {
            val tag = AppCompatDelegate.getApplicationLocales()
                .takeUnless { it.isEmpty }
                ?.get(0)
                ?.toLanguageTag()
                ?: return SYSTEM
            return fromTag(tag)
        }

        /** Best-effort match of a BCP-47 [tag] to one of our supported languages. */
        fun fromTag(tag: String): AppLanguage {
            val locale = Locale.forLanguageTag(tag)
            return entries.firstOrNull { language ->
                val candidate = language.languageTag?.let(Locale::forLanguageTag) ?: return@firstOrNull false
                candidate.language == locale.language &&
                    (candidate.country.isEmpty() || candidate.country.equals(locale.country, ignoreCase = true))
            } ?: SYSTEM
        }

        /** Applies [language] as the per-app locale, recreating activities as needed. */
        fun apply(language: AppLanguage) {
            val locales = language.languageTag
                ?.let { LocaleListCompat.forLanguageTags(it) }
                ?: LocaleListCompat.getEmptyLocaleList()
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}

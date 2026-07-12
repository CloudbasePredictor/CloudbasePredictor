package com.cloudbasepredictor.web.i18n

/**
 * Languages offered by the web app's Language setting. [SYSTEM] follows the browser locale. Names are
 * endonyms (shown in their own language) as is conventional for a language picker. Georgian, Thai and
 * Simplified Chinese are selectable but currently fall back to English until real translations exist.
 */
enum class WebLanguage(val tag: String?, val displayName: String) {
    SYSTEM(tag = null, displayName = "System"),
    ENGLISH(tag = "en", displayName = "English"),
    GERMAN(tag = "de", displayName = "Deutsch"),
    SPANISH(tag = "es", displayName = "Español"),
    FRENCH(tag = "fr", displayName = "Français"),
    PORTUGUESE(tag = "pt", displayName = "Português"),
    RUSSIAN(tag = "ru", displayName = "Русский"),
    GEORGIAN(tag = "ka", displayName = "ქართული"),
    THAI(tag = "th", displayName = "ไทย"),
    CHINESE(tag = "zh-CN", displayName = "简体中文"),
    ;

    /** Resolves [SYSTEM] to a concrete language using the browser locale; other values map to self. */
    fun resolve(systemTag: String?): WebLanguage =
        if (this == SYSTEM) fromTag(systemTag) else this

    companion object {
        /** Selectable languages excluding [SYSTEM], in menu order. */
        val selectable: List<WebLanguage> = entries.filterNot { it == SYSTEM }

        /** Maps a BCP-47 tag (e.g. "de-DE") to a supported language by its primary subtag, else English. */
        fun fromTag(tag: String?): WebLanguage {
            val primary = tag?.substringBefore('-')?.lowercase()
            return selectable.firstOrNull { it.tag?.substringBefore('-')?.lowercase() == primary }
                ?: ENGLISH
        }
    }
}

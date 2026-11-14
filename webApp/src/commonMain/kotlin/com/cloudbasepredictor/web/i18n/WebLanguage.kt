package com.cloudbasepredictor.web.i18n

/**
 * Languages known to the web app. [SYSTEM] follows the browser locale. Names are endonyms (shown in
 * their own language) as is conventional for a language picker. Every entry has a full [WebStrings]
 * translation provided by [webStringsFor].
 *
 * The whole web UI is drawn on a Skia canvas, which can only use fonts loaded into the Wasm runtime;
 * it never falls back to the fonts installed on the visitor's device. The bundled font draws Latin
 * and Cyrillic text. Text in the Georgian, Thai and Chinese scripts is drawn as empty boxes, so a
 * language is offered — and picked up from the browser locale — only while [canvasFontAvailable] is
 * true. The translations of the other languages stay in place and become reachable again once a font
 * for their script is loaded.
 */
enum class WebLanguage(
    val tag: String?,
    val displayName: String,
    val canvasFontAvailable: Boolean = true,
) {
    SYSTEM(tag = null, displayName = "System"),
    ENGLISH(tag = "en", displayName = "English"),
    GERMAN(tag = "de", displayName = "Deutsch"),
    SPANISH(tag = "es", displayName = "Español"),
    FRENCH(tag = "fr", displayName = "Français"),
    PORTUGUESE(tag = "pt", displayName = "Português"),
    RUSSIAN(tag = "ru", displayName = "Русский"),
    GEORGIAN(tag = "ka", displayName = "ქართული", canvasFontAvailable = false),
    THAI(tag = "th", displayName = "ไทย", canvasFontAvailable = false),
    CHINESE(tag = "zh-CN", displayName = "简体中文", canvasFontAvailable = false),
    ;

    /**
     * Resolves this preference to the language whose strings are shown. [SYSTEM] follows the browser
     * locale; a language the canvas cannot draw falls back to English instead of showing empty boxes.
     */
    fun resolve(systemTag: String?): WebLanguage = when {
        this == SYSTEM -> fromTag(systemTag)
        canvasFontAvailable -> this
        else -> ENGLISH
    }

    companion object {
        /** Languages the user can pick, excluding [SYSTEM], in menu order. */
        val selectable: List<WebLanguage> = entries.filter { it != SYSTEM && it.canvasFontAvailable }

        /** Options of the Language setting: [SYSTEM] first, then every [selectable] language. */
        val menuOptions: List<WebLanguage> = listOf(SYSTEM) + selectable

        /** Maps a BCP-47 tag (e.g. "de-DE") to a [selectable] language by its primary subtag, else English. */
        fun fromTag(tag: String?): WebLanguage {
            val primary = tag?.substringBefore('-')?.lowercase()
            return selectable.firstOrNull { it.tag?.substringBefore('-')?.lowercase() == primary }
                ?: ENGLISH
        }
    }
}

/**
 * True when every letter of this text belongs to a script the bundled canvas font is known to draw
 * (Latin and Cyrillic). Digits, punctuation and symbols are ignored: they do not identify a script.
 */
internal fun String.usesOnlyCanvasFontScripts(): Boolean =
    none { char -> char.isLetter() && canvasFontLetterRanges.none { range -> char in range } }

private val canvasFontLetterRanges = listOf(
    '\u0041'..'\u024F', // Basic Latin through Latin Extended-B
    '\u0400'..'\u052F', // Cyrillic and Cyrillic Supplement
    '\u1E00'..'\u1EFF', // Latin Extended Additional
)

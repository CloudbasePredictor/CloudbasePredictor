package com.cloudbasepredictor.web.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class WebLanguageTest {
    @Test
    fun fromTag_matchesPrimarySubtagElseEnglish() {
        assertEquals(WebLanguage.GERMAN, WebLanguage.fromTag("de-DE"))
        assertEquals(WebLanguage.ENGLISH, WebLanguage.fromTag("en-US"))
        assertEquals(WebLanguage.CHINESE, WebLanguage.fromTag("zh-CN"))
        assertEquals(WebLanguage.ENGLISH, WebLanguage.fromTag("xx"))
        assertEquals(WebLanguage.ENGLISH, WebLanguage.fromTag(null))
    }

    @Test
    fun resolve_systemUsesTagOtherwiseSelf() {
        assertEquals(WebLanguage.FRENCH, WebLanguage.SYSTEM.resolve("fr-CH"))
        assertEquals(WebLanguage.RUSSIAN, WebLanguage.RUSSIAN.resolve("de-DE"))
    }

    @Test
    fun untranslatedLocalesFallBackToEnglishStrings() {
        assertEquals(englishWebStrings, webStringsFor(WebLanguage.THAI))
        assertEquals(germanWebStrings, webStringsFor(WebLanguage.GERMAN))
    }
}

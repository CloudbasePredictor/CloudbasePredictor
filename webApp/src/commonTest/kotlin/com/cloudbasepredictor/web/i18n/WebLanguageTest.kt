package com.cloudbasepredictor.web.i18n

import com.cloudbasepredictor.model.WeatherCondition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WebLanguageTest {
    @Test
    fun fromTag_matchesPrimarySubtagElseEnglish() {
        assertEquals(WebLanguage.GERMAN, WebLanguage.fromTag("de-DE"))
        assertEquals(WebLanguage.ENGLISH, WebLanguage.fromTag("en-US"))
        assertEquals(WebLanguage.RUSSIAN, WebLanguage.fromTag("ru"))
        assertEquals(WebLanguage.ENGLISH, WebLanguage.fromTag("xx"))
        assertEquals(WebLanguage.ENGLISH, WebLanguage.fromTag(null))
    }

    @Test
    fun resolve_systemUsesTagOtherwiseSelf() {
        assertEquals(WebLanguage.FRENCH, WebLanguage.SYSTEM.resolve("fr-CH"))
        assertEquals(WebLanguage.RUSSIAN, WebLanguage.RUSSIAN.resolve("de-DE"))
        assertEquals(WebLanguage.ENGLISH, WebLanguage.SYSTEM.resolve(null))
    }

    @Test
    fun everyLanguageKeepsItsOwnTranslation() {
        assertEquals(germanWebStrings, webStringsFor(WebLanguage.GERMAN))
        assertEquals(georgianWebStrings, webStringsFor(WebLanguage.GEORGIAN))
        assertEquals(thaiWebStrings, webStringsFor(WebLanguage.THAI))
        assertEquals(chineseWebStrings, webStringsFor(WebLanguage.CHINESE))
        assertNotEquals(englishWebStrings, webStringsFor(WebLanguage.GEORGIAN))
        assertNotEquals(englishWebStrings, webStringsFor(WebLanguage.THAI))
        assertNotEquals(englishWebStrings, webStringsFor(WebLanguage.CHINESE))
    }

    // The canvas draws scripts without a loaded font as empty boxes, so such a language must be
    // unreachable from the menu, from a stored choice and from the browser locale.
    @Test
    fun languagesWithoutACanvasFontResolveToEnglishEverywhere() {
        val withoutFont = listOf(WebLanguage.GEORGIAN, WebLanguage.THAI, WebLanguage.CHINESE)

        assertEquals(withoutFont, WebLanguage.entries.filterNot { it.canvasFontAvailable })
        withoutFont.forEach { language ->
            assertFalse(language in WebLanguage.selectable, language.name)
            assertFalse(language in WebLanguage.menuOptions, language.name)
            assertEquals(WebLanguage.ENGLISH, language.resolve("de-DE"), language.name)
            assertEquals(WebLanguage.ENGLISH, WebLanguage.fromTag(language.tag), language.name)
            assertEquals(WebLanguage.ENGLISH, WebLanguage.SYSTEM.resolve(language.tag), language.name)
        }
        assertEquals(WebLanguage.ENGLISH, WebLanguage.SYSTEM.resolve("th-TH"))
        assertEquals(WebLanguage.ENGLISH, WebLanguage.SYSTEM.resolve("zh-Hans-CN"))
    }

    @Test
    fun menuOffersSystemFirstThenEveryLanguageTheCanvasCanDraw() {
        assertEquals(
            listOf(
                WebLanguage.SYSTEM,
                WebLanguage.ENGLISH,
                WebLanguage.GERMAN,
                WebLanguage.SPANISH,
                WebLanguage.FRENCH,
                WebLanguage.PORTUGUESE,
                WebLanguage.RUSSIAN,
            ),
            WebLanguage.menuOptions,
        )
    }

    // Guards the flag in both directions: a language cannot be offered while its translation needs a
    // script the bundled font lacks, and cannot stay hidden once its text is drawable.
    @Test
    fun canvasFontFlagMatchesTheScriptOfEveryTranslation() {
        WebLanguage.entries.filterNot { it == WebLanguage.SYSTEM }.forEach { language ->
            val strings = webStringsFor(language)
            val sample = listOf(
                language.displayName,
                strings.navMap,
                strings.navForecast,
                strings.navSettings,
                strings.navAbout,
                strings.actionSave,
                strings.actionCancel,
                strings.forecast[FORECAST_TODAY],
            ) + strings.forecast.weekdayShort + strings.forecast.monthShort

            assertEquals(
                language.canvasFontAvailable,
                sample.all { it.usesOnlyCanvasFontScripts() },
                language.name,
            )
        }
    }

    @Test
    fun scriptCheckIgnoresDigitsPunctuationAndSymbols() {
        assertTrue("Thermik 12 °C · 3,5 m/s — «Ёж», Übergänge".usesOnlyCanvasFontScripts())
        assertTrue("".usesOnlyCanvasFontScripts())
        assertFalse("ไทย".usesOnlyCanvasFontScripts())
        assertFalse("ქართული".usesOnlyCanvasFontScripts())
        assertFalse("12月".usesOnlyCanvasFontScripts())
    }

    @Test
    fun englishRemainsTheFallbackForUnknownLanguages() {
        assertEquals(englishWebStrings, webStringsFor(WebLanguage.ENGLISH))
        assertEquals(englishWebStrings, webStringsFor(WebLanguage.SYSTEM))
    }

    @Test
    fun everyLanguageHasCompleteForecastDateAndTemplateData() {
        WebLanguage.entries.forEach { language ->
            val forecast = webStringsFor(language).forecast
            assertEquals(7, forecast.weekdayShort.size, language.name)
            assertEquals(12, forecast.monthShort.size, language.name)
            assertTrue(forecast[FORECAST_TODAY].isNotBlank(), language.name)
            assertTrue(forecast[FORECAST_DAY_MONTH].contains("{day}"), language.name)
            assertTrue(forecast[FORECAST_DAY_MONTH].contains("{month}"), language.name)
            assertTrue(forecast[FORECAST_TEMPERATURE_CELSIUS].contains("{value}"), language.name)
            listOf(
                forecast[FORECAST_SUMMARY_THERMIC],
                forecast[FORECAST_SUMMARY_STUVE],
                forecast[FORECAST_SUMMARY_WIND],
                forecast[FORECAST_SUMMARY_CLOUD],
            ).forEach { summary ->
                assertTrue(summary.contains("{day}"), language.name)
                assertTrue(summary.contains("{place}"), language.name)
                assertTrue(summary.contains("{weather}"), language.name)
            }
            assertTrue(forecast[FORECAST_SUMMARY_THERMIC].contains("{high}"), language.name)
            assertTrue(forecast[FORECAST_SUMMARY_THERMIC].contains("{low}"), language.name)
            WeatherCondition.entries.forEach { condition ->
                assertTrue(forecast.weatherLabel(condition.ordinal).isNotBlank(), language.name)
            }
        }
    }

    @Test
    fun weatherLabelsFollowSharedConditionOrder() {
        val labels = WeatherCondition.entries.map { condition ->
            englishWebForecastStrings.weatherLabel(condition.ordinal)
        }

        assertEquals(
            listOf(
                "Clear sky",
                "Partly cloudy",
                "Fog",
                "Drizzle",
                "Rain",
                "Snow",
                "Rain showers",
                "Snow showers",
                "Thunderstorm",
                "Unknown weather",
            ),
            labels,
        )
    }
}

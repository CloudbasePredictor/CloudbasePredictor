package com.cloudbasepredictor.web.i18n

import com.cloudbasepredictor.model.WeatherCondition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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
    fun everySelectableLanguageResolvesToStrings() {
        assertEquals(germanWebStrings, webStringsFor(WebLanguage.GERMAN))
        assertEquals(georgianWebStrings, webStringsFor(WebLanguage.GEORGIAN))
        assertEquals(thaiWebStrings, webStringsFor(WebLanguage.THAI))
        assertEquals(chineseWebStrings, webStringsFor(WebLanguage.CHINESE))
    }

    @Test
    fun georgianThaiAndChineseNoLongerFallBackToEnglish() {
        assertNotEquals(englishWebStrings, webStringsFor(WebLanguage.GEORGIAN))
        assertNotEquals(englishWebStrings, webStringsFor(WebLanguage.THAI))
        assertNotEquals(englishWebStrings, webStringsFor(WebLanguage.CHINESE))
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

package com.cloudbasepredictor.web

import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.PlaceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebRouteStateCodecTest {
    @Test
    fun stuveHourIsKeptWithinTheRenderedForecastWindow() {
        assertEquals(12, WebRouteStateCodec.decode("#/?lat=47&lon=11&hour=23").hour)
        val location = PlaceLocation(47.0, 11.0, "Test")
        val encoded = WebRouteStateCodec.encodeFragment(
            WebRouteState(location = location, hour = 2),
        )
        assertTrue(encoded.contains("hour=6"))
    }

    @Test
    fun everyDestinationRoundTripsThroughFragment() {
        WebDestination.entries.forEach { destination ->
            val state = WebRouteState(destination)

            assertEquals(state, WebRouteStateCodec.decode(WebRouteStateCodec.encodeFragment(state)))
        }
    }

    @Test
    fun rootAndUnknownFragmentsFallBackToForecast() {
        val forecast = WebRouteState(WebDestination.Forecast)

        assertEquals(forecast, WebRouteStateCodec.decode(null))
        assertEquals(forecast, WebRouteStateCodec.decode("#/"))
        assertEquals(forecast, WebRouteStateCodec.decode("#/not-a-route"))
    }

    @Test
    fun persistedModelBecomesTheDefaultWhenTheUrlDoesNotOverrideIt() {
        assertEquals(
            ForecastModel.ECMWF_IFS,
            WebRouteStateCodec.decode(
                fragment = "#/",
                defaultModel = ForecastModel.ECMWF_IFS,
            ).model,
        )
    }

    @Test
    fun decoderAcceptsQueriesAndTrailingSlashes() {
        assertEquals(
            WebRouteState(WebDestination.Favorites),
            WebRouteStateCodec.decode("#/favorites/?from=forecast"),
        )
    }

    @Test
    fun encodedPathIsScopedToGitHubPagesBasePath() {
        assertEquals(
            "/CloudbasePredictor/#/settings",
            WebRouteStateCodec.encodePath(WebRouteState(WebDestination.Settings)),
        )
        assertEquals(
            "/preview/#/map",
            WebRouteStateCodec.encodePath(
                state = WebRouteState(WebDestination.Map),
                basePath = "preview",
            ),
        )
    }

    @Test
    fun forecastStateRoundTripsThroughShareableHash() {
        val state = WebRouteState(
            destination = WebDestination.Forecast,
            location = PlaceLocation(47.6631, 11.5217, "Brauneck Süd"),
            model = ForecastModel.ICON_D2,
            mode = ForecastMode.STUVE,
            dayIndex = 2,
            hour = 15,
        )

        assertEquals(state, WebRouteStateCodec.decode(WebRouteStateCodec.encodeFragment(state)))
    }

    @Test
    fun invalidForecastQueryFallsBackToSafeDefaults() {
        assertEquals(
            WebRouteState(),
            WebRouteStateCodec.decode(
                "#/forecast?lat=999&lon=oops&model=missing&view=nope&day=-9&hour=99",
            ),
        )
    }
}

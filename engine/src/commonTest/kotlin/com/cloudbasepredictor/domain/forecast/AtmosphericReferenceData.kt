package com.cloudbasepredictor.domain.forecast

/** Fixed input/reference corpus computed without the Kotlin engine. See docs/forecast-calculation-references.md. */
internal object AtmosphericReferenceData {
    val humidity = listOf(
        HumidityReference(-20f, 700f, 1.257399876f, 1.119300187f),
        HumidityReference(0f, 1000f, 6.112f, 3.825042661f),
        HumidityReference(10f, 850f, 12.271695994f, 9.111539949f),
        HumidityReference(25f, 1000f, 31.674294362f, 20.345851585f),
    )

    val dryColumn = listOf(
        DryLevel(1000.000000f, 20.000000f, 0.0f),
        DryLevel(988.394290f, 19.022908f, 0.1f),
        DryLevel(976.885071f, 18.045817f, 0.2f),
        DryLevel(965.471861f, 17.068725f, 0.3f),
        DryLevel(954.154177f, 16.091633f, 0.4f),
        DryLevel(942.931540f, 15.114542f, 0.5f),
        DryLevel(931.803468f, 14.137450f, 0.6f),
        DryLevel(920.769483f, 13.160359f, 0.7f),
        DryLevel(909.829107f, 12.183267f, 0.8f),
        DryLevel(898.981861f, 11.206175f, 0.9f),
        DryLevel(888.227268f, 10.229084f, 1.0f),
        DryLevel(877.564854f, 9.251992f, 1.1f),
        DryLevel(866.994141f, 8.274900f, 1.2f),
        DryLevel(856.514657f, 7.297809f, 1.3f),
        DryLevel(846.125927f, 6.320717f, 1.4f),
        DryLevel(835.827479f, 5.343625f, 1.5f),
        DryLevel(825.618840f, 4.366534f, 1.6f),
        DryLevel(815.499540f, 3.389442f, 1.7f),
        DryLevel(805.469108f, 2.412351f, 1.8f),
        DryLevel(795.527075f, 1.435259f, 1.9f),
        DryLevel(785.672972f, 0.458167f, 2.0f),
    )

    val noHeating = SurfaceHeatingInput(
        hourOfDay = 0,
        shortwaveRadiationWm2 = 0f,
        cloudCoverLowPercent = 0f,
        cloudCoverMidPercent = 0f,
        cloudCoverHighPercent = 0f,
        precipitationMm = 0f,
        isDay = false,
    )

    data class HumidityReference(
        val dewPointC: Float,
        val pressureHpa: Float,
        val vaporPressureHpa: Float,
        val mixingRatioGKg: Float,
    )

    data class DryLevel(val pressureHpa: Float, val temperatureC: Float, val heightKm: Float)
}

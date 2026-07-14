package com.cloudbasepredictor.ui.text

import android.content.Context
import androidx.annotation.StringRes
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Resolves Android string resources for state holders that need localized, user-visible text
 * (for example the forecast summary produced by [com.cloudbasepredictor.ui.screens.forecast.ForecastViewModel]).
 *
 * Keeping this behind an interface lets view models emit fully localized text while staying
 * unit-testable with a plain fake instead of a device [Context].
 */
interface AppStringResources {
    fun getString(@StringRes resId: Int): String

    fun getString(@StringRes resId: Int, vararg formatArgs: Any): String
}

@SingleIn(AppScope::class)
class AndroidStringResources @Inject constructor(
    private val context: Context,
) : AppStringResources {
    override fun getString(resId: Int): String = context.getString(resId)

    override fun getString(resId: Int, vararg formatArgs: Any): String =
        context.getString(resId, *formatArgs)
}

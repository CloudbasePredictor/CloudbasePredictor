package com.cloudbasepredictor.data.local

import android.content.Context
import android.content.Intent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

@SingleIn(AppScope::class)
class DatabaseErrorManager @Inject constructor(
    private val context: Context,
) {
    private val _showError = MutableStateFlow(false)
    val showError: StateFlow<Boolean> = _showError

    fun reportError(error: Throwable) {
        Timber.e(error, "Database error reported")
        _showError.value = true
    }

    fun clearDatabaseAndRestart() {
        context.deleteDatabase("cloudbase_predictor.db")
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        }
        Runtime.getRuntime().exit(0)
    }
}

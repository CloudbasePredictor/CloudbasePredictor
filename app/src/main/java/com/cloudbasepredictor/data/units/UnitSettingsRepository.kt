package com.cloudbasepredictor.data.units

import android.content.SharedPreferences
import com.cloudbasepredictor.data.place.FavoritePlacesBackupStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface UnitSettingsRepository {
    val unitPreset: StateFlow<UnitPreset>
    val displayUnits: StateFlow<DisplayUnits>

    fun setUnitPreset(unitPreset: UnitPreset)
}

@SingleIn(AppScope::class)
class SharedPrefsUnitSettingsRepository @Inject constructor(
    private val prefs: SharedPreferences,
    private val backupStore: FavoritePlacesBackupStore,
) : UnitSettingsRepository {
    private val mutableUnitPreset = MutableStateFlow(loadUnitPreset())
    private val mutableDisplayUnits = MutableStateFlow(mutableUnitPreset.value.resolveDisplayUnits())

    override val unitPreset: StateFlow<UnitPreset> = mutableUnitPreset.asStateFlow()
    override val displayUnits: StateFlow<DisplayUnits> = mutableDisplayUnits.asStateFlow()

    override fun setUnitPreset(unitPreset: UnitPreset) {
        mutableUnitPreset.value = unitPreset
        mutableDisplayUnits.value = unitPreset.resolveDisplayUnits()
        prefs.edit().putString(KEY_UNIT_PRESET, unitPreset.name).apply()
        backupStore.saveUnitPreset(unitPreset)
    }

    private fun loadUnitPreset(): UnitPreset {
        prefs.getString(KEY_UNIT_PRESET, null)
            ?.let(::decodeUnitPreset)
            ?.let { return it }

        backupStore.readUnitPreset()?.let { restored ->
            prefs.edit().putString(KEY_UNIT_PRESET, restored.name).apply()
            return restored
        }

        return UnitPreset.METRIC_KMH
    }

    private fun decodeUnitPreset(value: String): UnitPreset? {
        return runCatching { UnitPreset.valueOf(value) }.getOrNull()
    }

    private companion object {
        const val KEY_UNIT_PRESET = "unit_preset"
    }
}

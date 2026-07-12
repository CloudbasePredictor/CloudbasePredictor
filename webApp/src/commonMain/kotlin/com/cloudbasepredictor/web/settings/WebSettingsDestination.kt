@file:Suppress("FunctionNaming", "LongParameterList", "TooManyFunctions")

package com.cloudbasepredictor.web.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.data.theme.ThemePreference
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.web.i18n.LocalWebStrings
import com.cloudbasepredictor.web.i18n.WebLanguage
import com.cloudbasepredictor.web.preferences.WebPreferencesState
import com.cloudbasepredictor.web.presentation.destinationColumnCount
import com.cloudbasepredictor.web.preview.WebDestinationPreviewData

@Composable
fun WebSettingsDestination(
    state: WebPreferencesState,
    onUnitPresetSelected: (UnitPreset) -> Unit,
    onThemePreferenceSelected: (ThemePreference) -> Unit,
    onMapLayerSelected: (MapLayerPreference) -> Unit,
    onStartWithFavoritesChanged: (Boolean) -> Unit,
    onForecastModelSelected: (ForecastModel) -> Unit,
    onShowLaunchSitesChanged: (Boolean) -> Unit,
    onLanguageSelected: (WebLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalWebStrings.current
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = DestinationMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(DestinationPadding),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(HeaderSpacing)) {
                Text(
                    text = strings.settingsTitle,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = strings.settingsSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (destinationColumnCount(maxWidth.value) == 2) {
                    WideSettingsContent(
                        state = state,
                        onUnitPresetSelected = onUnitPresetSelected,
                        onThemePreferenceSelected = onThemePreferenceSelected,
                        onMapLayerSelected = onMapLayerSelected,
                        onStartWithFavoritesChanged = onStartWithFavoritesChanged,
                        onForecastModelSelected = onForecastModelSelected,
                        onShowLaunchSitesChanged = onShowLaunchSitesChanged,
                        onLanguageSelected = onLanguageSelected,
                    )
                } else {
                    NarrowSettingsContent(
                        state = state,
                        onUnitPresetSelected = onUnitPresetSelected,
                        onThemePreferenceSelected = onThemePreferenceSelected,
                        onMapLayerSelected = onMapLayerSelected,
                        onStartWithFavoritesChanged = onStartWithFavoritesChanged,
                        onForecastModelSelected = onForecastModelSelected,
                        onShowLaunchSitesChanged = onShowLaunchSitesChanged,
                        onLanguageSelected = onLanguageSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun WideSettingsContent(
    state: WebPreferencesState,
    onUnitPresetSelected: (UnitPreset) -> Unit,
    onThemePreferenceSelected: (ThemePreference) -> Unit,
    onMapLayerSelected: (MapLayerPreference) -> Unit,
    onStartWithFavoritesChanged: (Boolean) -> Unit,
    onForecastModelSelected: (ForecastModel) -> Unit,
    onShowLaunchSitesChanged: (Boolean) -> Unit,
    onLanguageSelected: (WebLanguage) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SectionSpacing),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            UnitsSection(state.unitPreset, onUnitPresetSelected)
            ThemeSection(state.themePreference, onThemePreferenceSelected)
            StartupSection(state.startWithFavorites, onStartWithFavoritesChanged)
            LanguageSection(state.language, onLanguageSelected)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            MapLayerSection(state.mapLayer, onMapLayerSelected)
            LaunchSitesSection(state.showLaunchSites, onShowLaunchSitesChanged)
            ForecastModelSection(state.forecastModel, onForecastModelSelected)
        }
    }
}

@Composable
private fun NarrowSettingsContent(
    state: WebPreferencesState,
    onUnitPresetSelected: (UnitPreset) -> Unit,
    onThemePreferenceSelected: (ThemePreference) -> Unit,
    onMapLayerSelected: (MapLayerPreference) -> Unit,
    onStartWithFavoritesChanged: (Boolean) -> Unit,
    onForecastModelSelected: (ForecastModel) -> Unit,
    onShowLaunchSitesChanged: (Boolean) -> Unit,
    onLanguageSelected: (WebLanguage) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
        UnitsSection(state.unitPreset, onUnitPresetSelected)
        ThemeSection(state.themePreference, onThemePreferenceSelected)
        MapLayerSection(state.mapLayer, onMapLayerSelected)
        LaunchSitesSection(state.showLaunchSites, onShowLaunchSitesChanged)
        StartupSection(state.startWithFavorites, onStartWithFavoritesChanged)
        ForecastModelSection(state.forecastModel, onForecastModelSelected)
        LanguageSection(state.language, onLanguageSelected)
    }
}

@Composable
private fun UnitsSection(
    selected: UnitPreset,
    onSelected: (UnitPreset) -> Unit,
) {
    val strings = LocalWebStrings.current
    PreferenceOptionSection(
        title = strings.unitsTitle,
        description = strings.unitsDescription,
        options = UnitPreset.entries,
        selected = selected,
        onSelected = onSelected,
        label = UnitPreset::displayLabel,
        supportingText = UnitPreset::supportingText,
    )
}

@Composable
private fun ThemeSection(
    selected: ThemePreference,
    onSelected: (ThemePreference) -> Unit,
) {
    val strings = LocalWebStrings.current
    PreferenceOptionSection(
        title = strings.themeTitle,
        description = strings.themeDescription,
        options = ThemePreference.entries,
        selected = selected,
        onSelected = onSelected,
        label = ThemePreference::displayLabel,
        supportingText = ThemePreference::supportingText,
    )
}

@Composable
private fun MapLayerSection(
    selected: MapLayerPreference,
    onSelected: (MapLayerPreference) -> Unit,
) {
    val strings = LocalWebStrings.current
    PreferenceOptionSection(
        title = strings.mapLayerTitle,
        description = strings.mapLayerDescription,
        options = MapLayerPreference.entries,
        selected = selected,
        onSelected = onSelected,
        label = MapLayerPreference::label,
        supportingText = MapLayerPreference::supportingText,
    )
}

@Composable
private fun ForecastModelSection(
    selected: ForecastModel,
    onSelected: (ForecastModel) -> Unit,
) {
    val strings = LocalWebStrings.current
    PreferenceOptionSection(
        title = strings.forecastModelTitle,
        description = strings.forecastModelDescription,
        options = ForecastModel.entries,
        selected = selected,
        onSelected = onSelected,
        label = ForecastModel::displayName,
        supportingText = ForecastModel::description,
    )
}

@Composable
private fun LaunchSitesSection(
    showLaunchSites: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    val strings = LocalWebStrings.current
    SettingsCard(
        title = strings.launchSitesTitle,
        description = strings.launchSitesDescription,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = showLaunchSites,
                    role = Role.Switch,
                    onValueChange = onChanged,
                )
                .semantics {
                    contentDescription = if (showLaunchSites) {
                        "${strings.launchSitesToggleLabel} checked"
                    } else {
                        "${strings.launchSitesToggleLabel} unchecked"
                    }
                }
                .padding(vertical = OptionVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(OptionSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OptionTextSpacing),
            ) {
                Text(strings.launchSitesToggleLabel, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = strings.launchSitesToggleDescription,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = showLaunchSites, onCheckedChange = null)
        }
    }
}

@Composable
private fun StartupSection(
    startWithFavorites: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    val strings = LocalWebStrings.current
    SettingsCard(
        title = strings.startupTitle,
        description = strings.startupDescription,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = startWithFavorites,
                    role = Role.Switch,
                    onValueChange = onChanged,
                )
                .semantics {
                    contentDescription = if (startWithFavorites) {
                        "${strings.startupToggleControl} checked"
                    } else {
                        "${strings.startupToggleControl} unchecked"
                    }
                }
                .padding(vertical = OptionVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(OptionSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OptionTextSpacing),
            ) {
                Text(strings.startupToggleLabel, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = strings.startupToggleDescription,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = startWithFavorites, onCheckedChange = null)
        }
    }
}

@Composable
private fun LanguageSection(
    selected: WebLanguage,
    onSelected: (WebLanguage) -> Unit,
) {
    val strings = LocalWebStrings.current
    PreferenceOptionSection(
        title = strings.languageTitle,
        description = strings.languageDescription,
        options = WebLanguage.entries,
        selected = selected,
        onSelected = onSelected,
        label = WebLanguage::displayName,
        supportingText = { "" },
    )
}

@Composable
private fun <T> PreferenceOptionSection(
    title: String,
    description: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: (T) -> String,
    supportingText: (T) -> String,
) {
    SettingsCard(title = title, description = description) {
        Column(modifier = Modifier.selectableGroup()) {
            options.forEachIndexed { index, option ->
                if (index > 0) HorizontalDivider()
                PreferenceOption(
                    groupTitle = title,
                    label = label(option),
                    supportingText = supportingText(option),
                    selected = option == selected,
                    onSelected = { onSelected(option) },
                )
            }
        }
    }
}

@Composable
private fun PreferenceOption(
    groupTitle: String,
    label: String,
    supportingText: String,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelected,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = if (selected) {
                    "$groupTitle option: $label selected"
                } else {
                    "$groupTitle option: $label not selected"
                }
            }
            .padding(vertical = OptionVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(OptionSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(OptionTextSpacing),
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = supportingText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(CardContentSpacing),
        ) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            content()
        }
    }
}

private val UnitPreset.displayLabel: String
    get() = when (this) {
        UnitPreset.METRIC_KMH -> "Metric · km/h"
        UnitPreset.METRIC_MPS -> "Metric · m/s"
        UnitPreset.IMPERIAL -> "Imperial"
        UnitPreset.AVIATION -> "Aviation"
    }

private val UnitPreset.supportingText: String
    get() = when (this) {
        UnitPreset.METRIC_KMH -> "Meters, km/h, and m/s"
        UnitPreset.METRIC_MPS -> "Meters with wind and climb in m/s"
        UnitPreset.IMPERIAL -> "Feet, mph, and ft/min"
        UnitPreset.AVIATION -> "Feet, knots, and ft/min"
    }

private val ThemePreference.displayLabel: String
    get() = when (this) {
        ThemePreference.AUTO -> "System"
        ThemePreference.LIGHT -> "Light"
        ThemePreference.DARK -> "Dark"
    }

private val ThemePreference.supportingText: String
    get() = when (this) {
        ThemePreference.AUTO -> "Follow this device's appearance"
        ThemePreference.LIGHT -> "Always use the light theme"
        ThemePreference.DARK -> "Always use the dark theme"
    }

private val MapLayerPreference.supportingText: String
    get() = when (this) {
        MapLayerPreference.OPENFREEMAP -> "Fast vector streets and terrain context"
        MapLayerPreference.OPENTOPOMAP -> "Topographic contours and outdoor detail"
        MapLayerPreference.NASA_GIBS -> "Daily NASA satellite imagery"
        MapLayerPreference.ESRI_WORLD_IMAGERY -> "High-resolution Esri satellite imagery"
    }

private val DestinationMaxWidth = 1_080.dp
private val DestinationPadding = 24.dp
private val SectionSpacing = 24.dp
private val HeaderSpacing = 8.dp
private val CardPadding = 20.dp
private val CardContentSpacing = 12.dp
private val OptionSpacing = 12.dp
private val OptionTextSpacing = 2.dp
private val OptionVerticalPadding = 12.dp

@Preview
@Composable
private fun WebSettingsDestinationPreview() {
    MaterialTheme {
        WebSettingsDestination(
            state = WebDestinationPreviewData.preferences,
            onUnitPresetSelected = {},
            onThemePreferenceSelected = {},
            onMapLayerSelected = {},
            onStartWithFavoritesChanged = {},
            onForecastModelSelected = {},
            onShowLaunchSitesChanged = {},
            onLanguageSelected = {},
        )
    }
}

package com.cloudbasepredictor.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudbasepredictor.R
import com.cloudbasepredictor.data.datasource.DataSourcePreference
import com.cloudbasepredictor.data.theme.ThemePreference
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.ui.preview.PreviewData
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val dataSource by viewModel.dataSourcePreference.collectAsStateWithLifecycle()
    val theme by viewModel.themePreference.collectAsStateWithLifecycle()
    val unitPreset by viewModel.unitPreset.collectAsStateWithLifecycle()
    val showLaunchSites by viewModel.showLaunchSites.collectAsStateWithLifecycle()
    val startWithFavorites by viewModel.startWithFavorites.collectAsStateWithLifecycle()

    SettingsScreen(
        dataSource = dataSource,
        onDataSourceChanged = viewModel::setDataSource,
        theme = theme,
        onThemeChanged = viewModel::setTheme,
        unitPreset = unitPreset,
        onUnitPresetChanged = viewModel::setUnitPreset,
        showLaunchSites = showLaunchSites,
        onShowLaunchSitesChanged = viewModel::setShowLaunchSites,
        startWithFavorites = startWithFavorites,
        onStartWithFavoritesChanged = viewModel::setStartWithFavorites,
        onBack = onBack,
        onOpenAbout = onOpenAbout,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    dataSource: DataSourcePreference,
    onDataSourceChanged: (DataSourcePreference) -> Unit,
    theme: ThemePreference,
    onThemeChanged: (ThemePreference) -> Unit,
    unitPreset: UnitPreset,
    onUnitPresetChanged: (UnitPreset) -> Unit,
    showLaunchSites: Boolean,
    onShowLaunchSitesChanged: (Boolean) -> Unit,
    startWithFavorites: Boolean,
    onStartWithFavoritesChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                    Text(
                        text = stringResource(R.string.title_settings),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Data source dropdown
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_data_source),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val dataSourceLabels = mapOf(
                        DataSourcePreference.REAL to "Real",
                        DataSourcePreference.SIMULATED to "Simulated",
                        DataSourcePreference.FAKE to "Fake",
                    )
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            value = dataSourceLabels[dataSource] ?: dataSource.name,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            DataSourcePreference.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(text = dataSourceLabels[option] ?: option.name) },
                                    onClick = {
                                        onDataSourceChanged(option)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                // Theme dropdown
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val themeLabels = mapOf(
                        ThemePreference.AUTO to "Auto (system)",
                        ThemePreference.LIGHT to "Light",
                        ThemePreference.DARK to "Dark",
                    )
                    var themeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = themeExpanded,
                        onExpandedChange = { themeExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = themeLabels[theme] ?: theme.name,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = themeExpanded,
                            onDismissRequest = { themeExpanded = false },
                        ) {
                            ThemePreference.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(text = themeLabels[option] ?: option.name) },
                                    onClick = {
                                        onThemeChanged(option)
                                        themeExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                // Units dropdown
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_units),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val unitLabels = mapOf(
                        UnitPreset.METRIC_KMH to "Metric (km/h)",
                        UnitPreset.METRIC_MPS to "Metric (m/s)",
                        UnitPreset.IMPERIAL to "Imperial (mph, ft)",
                        UnitPreset.AVIATION to "Aviation (kt, ft)",
                    )
                    var unitExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = unitExpanded,
                        onExpandedChange = { unitExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = unitLabels[unitPreset] ?: unitPreset.name,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = unitExpanded,
                            onDismissRequest = { unitExpanded = false },
                        ) {
                            UnitPreset.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(text = unitLabels[option] ?: option.name) },
                                    onClick = {
                                        onUnitPresetChanged(option)
                                        unitExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_map),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_show_launch_sites),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = showLaunchSites,
                            onCheckedChange = onShowLaunchSitesChanged,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_start_with_favorites),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = startWithFavorites,
                            onCheckedChange = onStartWithFavoritesChanged,
                        )
                    }
                }

                // About button
                OutlinedButton(onClick = onOpenAbout) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(text = stringResource(R.string.action_about))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    CloudbasePredictorTheme {
        SettingsScreen(
            dataSource = PreviewData.settingsFakeDataSource,
            onDataSourceChanged = {},
            theme = PreviewData.settingsAutoTheme,
            onThemeChanged = {},
            unitPreset = PreviewData.settingsMetricMpsUnits,
            onUnitPresetChanged = {},
            showLaunchSites = PreviewData.settingsShowLaunchSites,
            onShowLaunchSitesChanged = {},
            startWithFavorites = PreviewData.settingsStartWithFavorites,
            onStartWithFavoritesChanged = {},
            onBack = {},
            onOpenAbout = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenRealPreview() {
    CloudbasePredictorTheme {
        SettingsScreen(
            dataSource = PreviewData.settingsRealDataSource,
            onDataSourceChanged = {},
            theme = PreviewData.settingsDarkTheme,
            onThemeChanged = {},
            unitPreset = PreviewData.settingsImperialUnits,
            onUnitPresetChanged = {},
            showLaunchSites = PreviewData.settingsShowLaunchSites,
            onShowLaunchSitesChanged = {},
            startWithFavorites = PreviewData.settingsStartWithFavorites,
            onStartWithFavoritesChanged = {},
            onBack = {},
            onOpenAbout = {},
        )
    }
}

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsScreenDarkPreview() {
    CloudbasePredictorTheme(darkTheme = true) {
        SettingsScreen(
            dataSource = PreviewData.settingsRealDataSource,
            onDataSourceChanged = {},
            theme = PreviewData.settingsDarkTheme,
            onThemeChanged = {},
            unitPreset = PreviewData.settingsAviationUnits,
            onUnitPresetChanged = {},
            showLaunchSites = false,
            onShowLaunchSitesChanged = {},
            startWithFavorites = false,
            onStartWithFavoritesChanged = {},
            onBack = {},
            onOpenAbout = {},
        )
    }
}

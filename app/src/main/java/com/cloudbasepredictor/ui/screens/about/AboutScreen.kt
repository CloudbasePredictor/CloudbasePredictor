package com.cloudbasepredictor.ui.screens.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.BuildConfig
import com.cloudbasepredictor.R
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme

private const val SOURCE_CODE_URL = "https://github.com/CloudbasePredictor/CloudbasePredictor"
private const val OPEN_METEO_URL = "https://open-meteo.com"
private const val PARAGLIDINGEARTH_URL = "https://paragliding.earth"
private const val OPENFREEMAP_URL = "https://openfreemap.org"
private const val OPENTOPOMAP_URL = "https://opentopomap.org/about"
private const val OPENMAPTILES_URL = "https://openmaptiles.org"
private const val OPENSTREETMAP_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
private const val NASA_GIBS_URL =
    "https://www.earthdata.nasa.gov/engage/open-data-services-software/earthdata-developer-portal/gibs-api"
private const val ESRI_WORLD_IMAGERY_URL =
    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer"
private const val MAPLIBRE_URL = "https://maplibre.org"

@Composable
fun AboutRoute(
    onBack: () -> Unit,
) {
    AboutScreen(onBack = onBack)
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    fun openUrl(url: String) {
        uriHandler.openUri(url)
    }

    Column(
        modifier = modifier.fillMaxSize(),
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
                    text = stringResource(R.string.title_about),
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
            AboutSection {
                Text(
                    text = stringResource(R.string.about_app_name),
                    style = MaterialTheme.typography.headlineMedium,
                )

                Text(
                    text = stringResource(
                        R.string.about_version_format,
                        BuildConfig.VERSION_NAME,
                        if (BuildConfig.DEBUG) "debug" else "release",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.about_foss_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    LinkText(
                        title = stringResource(R.string.about_source_code),
                        url = SOURCE_CODE_URL,
                        linkColor = linkColor,
                        onOpenUrl = ::openUrl,
                    )
                }
            }

            AboutSection(title = stringResource(R.string.about_data_sources)) {
                DataSourceGroup(
                    label = stringResource(R.string.about_forecast_data_label),
                    content = {
                        ProviderBlock(
                            primaryLink = DataSourceLink(
                                title = stringResource(R.string.about_open_meteo),
                                url = OPEN_METEO_URL,
                            ),
                            linkColor = linkColor,
                            onOpenUrl = ::openUrl,
                        )
                    },
                )

                DataSourceGroup(
                    label = stringResource(R.string.about_launch_sites_label),
                    content = {
                        ProviderBlock(
                            primaryLink = DataSourceLink(
                                title = stringResource(R.string.about_paraglidingearth),
                                url = PARAGLIDINGEARTH_URL,
                            ),
                            detail = stringResource(R.string.about_paraglidingearth_detail),
                            linkColor = linkColor,
                            onOpenUrl = ::openUrl,
                        )
                    },
                )

                DataSourceGroup(
                    label = stringResource(R.string.about_map_services_label),
                    content = {
                        ProviderBlock(
                            primaryLink = DataSourceLink(
                                title = stringResource(R.string.about_openfreemap),
                                url = OPENFREEMAP_URL,
                            ),
                            relatedLinks = listOf(
                                DataSourceLink(
                                    title = stringResource(R.string.about_openmaptiles_source),
                                    url = OPENMAPTILES_URL,
                                ),
                                DataSourceLink(
                                    title = stringResource(R.string.about_openstreetmap),
                                    url = OPENSTREETMAP_COPYRIGHT_URL,
                                ),
                            ),
                            linkColor = linkColor,
                            onOpenUrl = ::openUrl,
                        )

                        ProviderDivider()

                        ProviderBlock(
                            primaryLink = DataSourceLink(
                                title = stringResource(R.string.about_opentopomap),
                                url = OPENTOPOMAP_URL,
                            ),
                            detail = stringResource(R.string.about_opentopomap_detail),
                            relatedLinks = listOf(
                                DataSourceLink(
                                    title = stringResource(R.string.about_openstreetmap),
                                    url = OPENSTREETMAP_COPYRIGHT_URL,
                                ),
                            ),
                            linkColor = linkColor,
                            onOpenUrl = ::openUrl,
                        )

                        ProviderDivider()

                        ProviderBlock(
                            primaryLink = DataSourceLink(
                                title = stringResource(R.string.about_nasa_gibs),
                                url = NASA_GIBS_URL,
                            ),
                            linkColor = linkColor,
                            onOpenUrl = ::openUrl,
                        )

                        ProviderDivider()

                        ProviderBlock(
                            primaryLink = DataSourceLink(
                                title = stringResource(R.string.about_esri_world_imagery),
                                url = ESRI_WORLD_IMAGERY_URL,
                            ),
                            detail = stringResource(R.string.about_esri_world_imagery_detail),
                            linkColor = linkColor,
                            onOpenUrl = ::openUrl,
                        )
                    },
                )
            }

            AboutSection(title = stringResource(R.string.about_technology)) {
                DataSourceGroup(
                    label = stringResource(R.string.about_map_sdk_label),
                    content = {
                        ProviderBlock(
                            primaryLink = DataSourceLink(
                                title = stringResource(R.string.about_maplibre),
                                url = MAPLIBRE_URL,
                            ),
                            linkColor = linkColor,
                            onOpenUrl = ::openUrl,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AboutSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        content()
    }
}

@Composable
private fun DataSourceGroup(
    label: String,
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun ProviderBlock(
    primaryLink: DataSourceLink,
    linkColor: Color,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    relatedLinks: List<DataSourceLink> = emptyList(),
    detail: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ProviderTitleLink(
            link = primaryLink,
            linkColor = linkColor,
            onOpenUrl = onOpenUrl,
        )

        relatedLinks.forEach { link ->
            LinkText(
                title = link.title,
                url = link.url,
                linkColor = linkColor,
                onOpenUrl = onOpenUrl,
            )
        }

        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProviderTitleLink(
    link: DataSourceLink,
    linkColor: Color,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = link.title,
        style = MaterialTheme.typography.titleSmall.copy(
            color = linkColor,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline,
        ),
        modifier = modifier.clickable { onOpenUrl(link.url) },
    )
}

@Composable
private fun ProviderDivider(
    modifier: Modifier = Modifier,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun LinkText(
    title: String,
    url: String,
    linkColor: Color,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = linkColor,
            textDecoration = TextDecoration.Underline,
        ),
        modifier = modifier.clickable { onOpenUrl(url) },
    )
}

private data class DataSourceLink(
    val title: String,
    val url: String,
)

@Preview(showBackground = true)
@Composable
private fun AboutScreenPreview() {
    CloudbasePredictorTheme {
        AboutScreen(onBack = {})
    }
}

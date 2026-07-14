@file:Suppress("FunctionNaming")

package com.cloudbasepredictor.web.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.web.WebBuildConfig
import com.cloudbasepredictor.web.i18n.LocalWebStrings

/**
 * Web "About" screen. Mirrors the Android AboutScreen attribution content so the four navigation
 * destinations match the Android app. The version is injected at build time via [WebBuildConfig]
 * (generated from the `cloudbaseVersionName` Gradle property). Strings are English for now; Stage F
 * migrates them to shared Compose resources.
 */
@Suppress("LongMethod")
@Composable
fun WebAboutDestination(
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val openUrl: (String) -> Unit = uriHandler::openUri
    val strings = LocalWebStrings.current
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(ContentPadding),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(HeaderSpacing)) {
                Text(
                    text = strings.aboutTitle,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Cloudbase Predictor",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "${strings.aboutVersionPrefix} ${WebBuildConfig.VERSION} · ${strings.aboutPlatformWeb}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = strings.aboutFreeSoftware,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                AboutLink(title = strings.aboutSourceCode, url = SOURCE_CODE_URL, onOpenUrl = openUrl)
            }

            AboutSection(title = strings.aboutDataSources) {
                AboutProvider(title = strings.aboutForecastData) {
                    AboutLink(title = "Open-Meteo", url = OPEN_METEO_URL, onOpenUrl = openUrl)
                }
                AboutProvider(title = strings.launchSitesTitle) {
                    AboutLink(title = "ParaglidingEarth", url = PARAGLIDINGEARTH_URL, onOpenUrl = openUrl)
                    Text(
                        text = strings.aboutLaunchSitesDetail,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                AboutProvider(title = strings.aboutMapServices) {
                    AboutLink(title = "OpenFreeMap", url = OPENFREEMAP_URL, onOpenUrl = openUrl)
                    AboutLink(title = "OpenMapTiles", url = OPENMAPTILES_URL, onOpenUrl = openUrl)
                    AboutLink(title = "OpenStreetMap contributors", url = OPENSTREETMAP_URL, onOpenUrl = openUrl)
                    AboutLink(title = "OpenTopoMap", url = OPENTOPOMAP_URL, onOpenUrl = openUrl)
                    AboutLink(title = "NASA GIBS", url = NASA_GIBS_URL, onOpenUrl = openUrl)
                    AboutLink(title = "Esri World Imagery", url = ESRI_WORLD_IMAGERY_URL, onOpenUrl = openUrl)
                }
                AboutProvider(title = strings.aboutMapRendering) {
                    AboutLink(title = "MapLibre", url = MAPLIBRE_URL, onOpenUrl = openUrl)
                }
            }
        }
    }
}

@Composable
private fun AboutSection(
    title: String,
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun AboutProvider(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ProviderSpacing)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
        content()
    }
}

@Composable
private fun AboutLink(
    title: String,
    url: String,
    onOpenUrl: (String) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
        modifier = Modifier
            .clickable { onOpenUrl(url) }
            .semantics {
                role = Role.Button
                contentDescription = title
            },
    )
}

private const val SOURCE_CODE_URL = "https://github.com/CloudbasePredictor/CloudbasePredictor"
private const val OPEN_METEO_URL = "https://open-meteo.com"
private const val PARAGLIDINGEARTH_URL = "https://paragliding.earth"
private const val OPENFREEMAP_URL = "https://openfreemap.org"
private const val OPENMAPTILES_URL = "https://openmaptiles.org"
private const val OPENSTREETMAP_URL = "https://www.openstreetmap.org/copyright"
private const val OPENTOPOMAP_URL = "https://opentopomap.org/about"
private const val NASA_GIBS_URL =
    "https://www.earthdata.nasa.gov/engage/open-data-services-software/earthdata-developer-portal/gibs-api"
private const val ESRI_WORLD_IMAGERY_URL =
    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer"
private const val MAPLIBRE_URL = "https://maplibre.org"


private val ContentMaxWidth = 1_080.dp
private val ContentPadding = 24.dp
private val SectionSpacing = 24.dp
private val HeaderSpacing = 8.dp
private val CardPadding = 20.dp
private val CardContentSpacing = 16.dp
private val ProviderSpacing = 6.dp

@Preview(name = "Web about", showBackground = true, widthDp = 1024, heightDp = 760)
@Composable
private fun WebAboutDestinationPreview() {
    MaterialTheme {
        WebAboutDestination()
    }
}

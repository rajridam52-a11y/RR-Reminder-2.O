package com.example.features.weather

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.datastore.UserPreferences
import com.example.core.network.WeatherCodeInterpreter
import com.example.core.network.WeatherUnits
import com.example.core.notifications.ScheduleCalculator
import com.example.core.permissions.PermissionHelper
import com.example.ui.components.RRCard
import com.example.ui.components.RREmptyState
import com.example.ui.components.RRMetricBadge
import com.example.ui.components.RRSearchField
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.LocalRRExtraColors
import com.example.ui.theme.TabularClockLarge
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    uiState: WeatherUiState,
    preferences: UserPreferences,
    nowMillis: Long,
    onSearchCity: (String) -> Unit,
    onSelectCity: (String, Double, Double) -> Unit,
    onClearCityResults: () -> Unit,
    onUseLocation: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val extra = LocalRRExtraColors.current
    var cityQuery by remember { mutableStateOf("") }

    val locLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it } || PermissionHelper.hasAnyLocationPermission(context)) {
            onUseLocation()
        }
    }

    val requestOrFetchGps = {
        if (PermissionHelper.hasAnyLocationPermission(context)) {
            onUseLocation()
        } else {
            locLauncher.launch(PermissionHelper.LOCATION_PERMISSIONS)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Weather Center") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(
                        onClick = requestOrFetchGps,
                        modifier = Modifier.testTag("weather_gps_btn"),
                    ) {
                        Icon(Icons.Rounded.MyLocation, contentDescription = "Use real GPS location")
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("weather_refresh_btn"),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh live forecast")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp),
        ) {
            item {
                RRSearchField(
                    query = cityQuery,
                    onQueryChange = {
                        cityQuery = it
                        onSearchCity(it)
                    },
                    placeholder = "Search city (e.g., London, Tokyo, Seattle)...",
                    testTag = "weather_city_search",
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = requestOrFetchGps,
                        modifier = Modifier.testTag("weather_live_gps_chip"),
                    ) {
                        Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Live GPS Forecast")
                    }
                    if (uiState.isGpsSourced && uiState.gpsProviderInfo != null) {
                        RRStatusPill(
                            text = uiState.gpsProviderInfo,
                            color = extra.success,
                            icon = Icons.Rounded.GpsFixed,
                        )
                    }
                }
            }

            if (uiState.isSearchingCities) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            if (uiState.citySearchResults.isNotEmpty()) {
                item {
                    RRCard {
                        Text("Select City", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        uiState.citySearchResults.forEach { res ->
                            val label = listOfNotNull(res.name, res.admin1, res.country).joinToString(", ")
                            TextButton(
                                onClick = {
                                    cityQuery = ""
                                    onClearCityResults()
                                    onSelectCity(res.name, res.latitude, res.longitude)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(label, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            if (uiState.isShowingCachedData && uiState.cache != null) {
                item {
                    RRCard(containerColor = extra.warning.copy(alpha = 0.14f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.CloudQueue, contentDescription = null, tint = extra.warning)
                            Text(
                                text = "Showing offline cached forecast from ${ScheduleCalculator.formatTime(uiState.cache.updatedAtMillis, preferences.use24Hour)}.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            uiState.errorMessage?.let { err ->
                item {
                    RRCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(err, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            if (uiState.isGpsDisabled) {
                                TextButton(onClick = { PermissionHelper.openLocationSettings(context) }) {
                                    Text("Enable GPS")
                                }
                            } else {
                                TextButton(onClick = onRefresh) { Text("Retry") }
                            }
                        }
                    }
                }
            }

            val cache = uiState.cache
            if (cache == null && !uiState.isLoading) {
                item {
                    RREmptyState(
                        icon = Icons.Rounded.WbCloudy,
                        title = "No city selected yet",
                        subtitle = "Tap Live GPS Forecast or search any city above to fetch real-time forecasts from Open-Meteo.",
                        actionLabel = "Use Live GPS",
                        onActionClick = requestOrFetchGps,
                    )
                }
            } else if (cache != null) {
                val advisory = WeatherCodeInterpreter.severeAlertFor(cache.weatherCode, cache.windKmh)
                if (advisory != null) {
                    item {
                        RRCard(containerColor = extra.warning.copy(alpha = 0.16f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = extra.warning)
                                Text(advisory, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                item {
                    RRCard(accentGlow = true) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cache.cityName, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    text = WeatherCodeInterpreter.describe(cache.weatherCode),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = extra.accentPrimary,
                                )
                                Text(
                                    text = "Updated ${ScheduleCalculator.formatTime(cache.updatedAtMillis, preferences.use24Hour, includeSeconds = true)} • Open-Meteo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = String.format(Locale.US, "Coordinates: %.4f, %.4f", cache.latitude, cache.longitude),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = WeatherUnits.formatTemp(cache.currentTempC, preferences.useFahrenheit),
                                style = TabularClockLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RRMetricBadge(
                                label = "Feels Like",
                                value = WeatherUnits.formatTemp(cache.feelsLikeC, preferences.useFahrenheit),
                                color = extra.accentPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            RRMetricBadge(
                                label = "Humidity",
                                value = "${cache.humidityPercent}%",
                                color = extra.info,
                                modifier = Modifier.weight(1f),
                            )
                            RRMetricBadge(
                                label = "Wind",
                                value = WeatherUnits.formatWind(cache.windKmh, preferences.useMph, cache.windDirectionDeg),
                                color = extra.accentSecondary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RRMetricBadge(
                                label = "Pressure",
                                value = if (cache.pressureHpa > 0) "${cache.pressureHpa.toInt()} hPa" else "--",
                                color = extra.info,
                                modifier = Modifier.weight(1f),
                            )
                            RRMetricBadge(
                                label = "UV Index",
                                value = "${String.format(Locale.US, "%.1f", cache.uvIndex)} ${WeatherUnits.describeUv(cache.uvIndex)}",
                                color = extra.warning,
                                modifier = Modifier.weight(1f),
                            )
                            RRMetricBadge(
                                label = "Visibility",
                                value = WeatherUnits.formatVisibility(cache.visibilityMeters, preferences.useMph),
                                color = extra.accentPrimary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (cache.precipitationMm > 0.0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            RRStatusPill(
                                text = "Precipitation: ${String.format(Locale.US, "%.1f", cache.precipitationMm)} mm",
                                color = extra.info,
                                icon = Icons.Rounded.WaterDrop,
                            )
                        }
                        if (cache.sunriseIso.isNotBlank() && cache.sunsetIso.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "Sunrise: ${WeatherUnits.formatIsoTime(cache.sunriseIso, preferences.use24Hour)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Sunset: ${WeatherUnits.formatIsoTime(cache.sunsetIso, preferences.use24Hour)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                val upcomingHourly = WeatherUnits.sliceHourlyFromNow(
                    items = uiState.hourly,
                    timezoneId = cache.apiTimezone,
                    nowMillis = nowMillis,
                    limit = 24,
                )
                if (upcomingHourly.isNotEmpty()) {
                    item {
                        RRCard {
                            Text("Hourly Forecast (Next 24h)", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                upcomingHourly.forEach { h ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(68.dp),
                                    ) {
                                        Text(
                                            text = WeatherUnits.formatIsoTime(h.timeIso, preferences.use24Hour),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = WeatherUnits.formatTempShort(h.tempC, preferences.useFahrenheit),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Text(
                                            "${h.rainProbPercent}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = extra.info,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (uiState.daily.isNotEmpty()) {
                    item {
                        Text("7-Day Forecast", style = MaterialTheme.typography.titleMedium)
                    }
                    items(uiState.daily) { day ->
                        RRCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(day.dateIso, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        buildString {
                                            append(WeatherCodeInterpreter.describe(day.weatherCode))
                                            if (day.precipitationSumMm > 0.0) {
                                                append(" • ${String.format(Locale.US, "%.1f", day.precipitationSumMm)} mm")
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                RRStatusPill(
                                    text = "${day.rainProbPercent}% rain",
                                    color = extra.info,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "${WeatherUnits.formatTempShort(day.minTempC, preferences.useFahrenheit)} / ${WeatherUnits.formatTempShort(day.maxTempC, preferences.useFahrenheit)}",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

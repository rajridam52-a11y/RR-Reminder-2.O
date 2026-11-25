package com.example.features.weather

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.datastore.UserPreferences
import com.example.core.network.WeatherCodeInterpreter
import com.example.core.network.WeatherUnits
import com.example.core.notifications.ScheduleCalculator
import com.example.core.permissions.PermissionHelper
import com.example.ui.components.RRCard
import com.example.ui.components.RREmptyState
import com.example.ui.components.RRSearchField
import com.example.ui.components.RRStatusPill
import com.example.ui.components.WeatherMountainCanvas
import com.example.ui.theme.LocalRRExtraColors
import com.example.ui.theme.SpaceGroteskFontFamily
import java.time.LocalDate
import java.time.format.TextStyle
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
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val extra = LocalRRExtraColors.current
    var cityQuery by remember { mutableStateOf("") }
    var showSearchBar by remember(uiState.cache == null) { mutableStateOf(uiState.cache == null) }
    var expandAllForecast by remember { mutableStateOf(false) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        WeatherMountainCanvas(modifier = Modifier.fillMaxSize())

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Weather",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("weather_back_btn")) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showSearchBar = !showSearchBar },
                            modifier = Modifier.testTag("weather_toggle_search_btn"),
                        ) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = "Search city",
                                tint = Color.White,
                            )
                        }
                        IconButton(
                            onClick = requestOrFetchGps,
                            modifier = Modifier.testTag("weather_gps_btn"),
                        ) {
                            Icon(
                                Icons.Rounded.MyLocation,
                                contentDescription = "Use real GPS location",
                                tint = Color(0xFF7DD3FC),
                            )
                        }
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.testTag("weather_refresh_btn"),
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Refresh live forecast",
                                tint = Color.White,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 104.dp),
            ) {
                item {
                    AnimatedVisibility(visible = showSearchBar || uiState.cache == null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RRSearchField(
                                query = cityQuery,
                                onQueryChange = {
                                    cityQuery = it
                                    onSearchCity(it)
                                },
                                placeholder = "Search city (e.g., Raipur, London, Tokyo)...",
                                testTag = "weather_city_search",
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0x8812356B),
                                    border = BorderStroke(1.dp, Color(0x557DD3FC)),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable(onClick = requestOrFetchGps)
                                        .testTag("weather_live_gps_chip"),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.MyLocation,
                                            contentDescription = null,
                                            tint = Color(0xFF7DD3FC),
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = "Live GPS Forecast",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White,
                                        )
                                    }
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
                    }
                }

                if (uiState.isSearchingCities) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF38BDF8),
                        )
                    }
                }

                if (uiState.citySearchResults.isNotEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xCC0A1C3C),
                            border = BorderStroke(1.dp, Color(0x4460A5FA)),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    "Select City",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                uiState.citySearchResults.forEach { res ->
                                    val label = listOfNotNull(res.name, res.admin1, res.country).joinToString(", ")
                                    TextButton(
                                        onClick = {
                                            cityQuery = ""
                                            showSearchBar = false
                                            onClearCityResults()
                                            onSelectCity(res.name, res.latitude, res.longitude)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(label, color = Color(0xFFE0ECFF), modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                if (uiState.isLoading) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF38BDF8),
                        )
                    }
                }

                if (uiState.isShowingCachedData && uiState.cache != null) {
                    item {
                        RRCard(containerColor = extra.warning.copy(alpha = 0.22f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Rounded.CloudQueue, contentDescription = null, tint = extra.warning)
                                Text(
                                    text = "Showing offline cached forecast from ${ScheduleCalculator.formatTime(uiState.cache.updatedAtMillis, preferences.use24Hour)}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                uiState.errorMessage?.let { err ->
                    item {
                        RRCard(containerColor = Color(0xCC4C1D2D)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = Color(0xFFFDA4AF))
                                Text(err, style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.weight(1f))
                                if (uiState.isGpsDisabled) {
                                    TextButton(onClick = { PermissionHelper.openLocationSettings(context) }) {
                                        Text("Enable GPS", color = Color(0xFF7DD3FC))
                                    }
                                } else {
                                    TextButton(onClick = onRefresh) { Text("Retry", color = Color(0xFF7DD3FC)) }
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
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xBB422006),
                                border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.55f)),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Color(0xFFFBBF24))
                                    Text(advisory, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                }
                            }
                        }
                    }

                    // Screen 6 Hero Weather Block
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0x44091B3A),
                                border = BorderStroke(1.dp, Color(0x447DD3FC)),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { showSearchBar = !showSearchBar },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.LocationOn,
                                        contentDescription = "Change city",
                                        tint = Color(0xFF7DD3FC),
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = cache.cityName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White,
                                        maxLines = 1,
                                    )
                                    if (uiState.isGpsSourced) {
                                        Icon(
                                            Icons.Rounded.GpsFixed,
                                            contentDescription = "GPS sourced",
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                WeatherHeroGraphic(
                                    weatherCode = cache.weatherCode,
                                    modifier = Modifier.size(104.dp),
                                )
                                Spacer(modifier = Modifier.width(18.dp))
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        text = WeatherUnits.formatTemp(cache.currentTempC, preferences.useFahrenheit),
                                        style = MaterialTheme.typography.displayLarge.copy(
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 54.sp,
                                            lineHeight = 56.sp,
                                        ),
                                        color = Color.White,
                                    )
                                    Text(
                                        text = WeatherCodeInterpreter.describe(cache.weatherCode),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                                        color = Color(0xFFE0F2FE),
                                    )
                                    Text(
                                        text = "Updated ${ScheduleCalculator.formatTime(cache.updatedAtMillis, preferences.use24Hour)} • Open-Meteo",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF93C5FD),
                                    )
                                }
                            }
                        }
                    }

                    // Screen 6 Three Translucent Metric Tiles: Humidity | Wind | Rain
                    item {
                        val todayRainProb = uiState.daily.firstOrNull()?.rainProbPercent
                            ?: uiState.hourly.firstOrNull()?.rainProbPercent
                            ?: 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            WeatherGlassMetricTile(
                                icon = Icons.Rounded.WaterDrop,
                                iconTint = Color(0xFF38BDF8),
                                label = "Humidity",
                                value = "${cache.humidityPercent}%",
                                modifier = Modifier.weight(1f),
                            )
                            WeatherGlassMetricTile(
                                icon = Icons.Rounded.Air,
                                iconTint = Color(0xFF7DD3FC),
                                label = "Wind",
                                value = WeatherUnits.formatWind(cache.windKmh, preferences.useMph, null),
                                modifier = Modifier.weight(1f),
                            )
                            WeatherGlassMetricTile(
                                icon = Icons.Rounded.Umbrella,
                                iconTint = Color(0xFF60A5FA),
                                label = "Rain",
                                value = "$todayRainProb%",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // Secondary compact atmosphere telemetry strip (Feels Like, UV, Pressure, Visibility, Sun times)
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0x77071732),
                            border = BorderStroke(1.dp, Color(0x3360A5FA)),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    SmallTelemetryTag("Feels", WeatherUnits.formatTemp(cache.feelsLikeC, preferences.useFahrenheit))
                                    SmallTelemetryTag("UV", "${String.format(Locale.US, "%.1f", cache.uvIndex)} ${WeatherUnits.describeUv(cache.uvIndex)}")
                                    SmallTelemetryTag("Pressure", if (cache.pressureHpa > 0) "${cache.pressureHpa.toInt()} hPa" else "--")
                                    SmallTelemetryTag("Vis", WeatherUnits.formatVisibility(cache.visibilityMeters, preferences.useMph))
                                    if (cache.windDirectionDeg != null) {
                                        SmallTelemetryTag("Dir", WeatherUnits.degreesToCompass(cache.windDirectionDeg))
                                    }
                                }
                                if (cache.sunriseIso.isNotBlank() && cache.sunsetIso.isNotBlank()) {
                                    HorizontalDivider(color = Color(0x2293C5FD))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "Sunrise ${WeatherUnits.formatIsoTime(cache.sunriseIso, preferences.use24Hour)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFFFDE68A),
                                        )
                                        Text(
                                            text = "Sunset ${WeatherUnits.formatIsoTime(cache.sunsetIso, preferences.use24Hour)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFF93C5FD),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Screen 6 Bottom Translucent Card: 7-Day Forecast + View All toggle
                    if (uiState.daily.isNotEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = Color(0xAA071732),
                                border = BorderStroke(1.dp, Color(0x4460A5FA)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "7-Day Forecast",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .clickable { expandAllForecast = !expandAllForecast }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                .testTag("weather_view_all_btn"),
                                        ) {
                                            Text(
                                                text = if (expandAllForecast) "Compact" else "View All",
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                                color = Color(0xFF93C5FD),
                                            )
                                            Icon(
                                                imageVector = if (expandAllForecast) Icons.Rounded.KeyboardArrowUp else Icons.AutoMirrored.Rounded.ArrowForward,
                                                contentDescription = null,
                                                tint = Color(0xFF93C5FD),
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }

                                    // 4-day horizontal summary strip matching Screen 6
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        uiState.daily.take(4).forEach { day ->
                                            val dayName = runCatching {
                                                LocalDate.parse(day.dateIso).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                                            }.getOrDefault(day.dateIso.takeLast(5))
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Text(
                                                    text = dayName,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = Color(0xFFBFD7FF),
                                                )
                                                Icon(
                                                    imageVector = weatherCodeIcon(day.weatherCode),
                                                    contentDescription = WeatherCodeInterpreter.describe(day.weatherCode),
                                                    tint = weatherCodeTint(day.weatherCode),
                                                    modifier = Modifier.size(28.dp),
                                                )
                                                Text(
                                                    text = WeatherUnits.formatTempShort(day.maxTempC, preferences.useFahrenheit),
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White,
                                                )
                                            }
                                        }
                                    }

                                    // Full 7-day breakdown when expanded
                                    AnimatedVisibility(visible = expandAllForecast) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            HorizontalDivider(color = Color(0x3393C5FD))
                                            uiState.daily.forEach { day ->
                                                val dayLabel = runCatching {
                                                    val d = LocalDate.parse(day.dateIso)
                                                    "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${d.dayOfMonth}/${d.monthValue}"
                                                }.getOrDefault(day.dateIso)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.weight(1f),
                                                    ) {
                                                        Icon(
                                                            imageVector = weatherCodeIcon(day.weatherCode),
                                                            contentDescription = null,
                                                            tint = weatherCodeTint(day.weatherCode),
                                                            modifier = Modifier.size(20.dp),
                                                        )
                                                        Column {
                                                            Text(dayLabel, style = MaterialTheme.typography.labelLarge, color = Color.White)
                                                            Text(
                                                                WeatherCodeInterpreter.describe(day.weatherCode),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = Color(0xFF93C5FD),
                                                            )
                                                        }
                                                    }
                                                    RRStatusPill(
                                                        text = "${day.rainProbPercent}%",
                                                        color = Color(0xFF38BDF8),
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        text = "${WeatherUnits.formatTempShort(day.minTempC, preferences.useFahrenheit)} / ${WeatherUnits.formatTempShort(day.maxTempC, preferences.useFahrenheit)}",
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = Color.White,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Hourly Forecast (Next 24h) card
                    val upcomingHourly = WeatherUnits.sliceHourlyFromNow(
                        items = uiState.hourly,
                        timezoneId = cache.apiTimezone,
                        nowMillis = nowMillis,
                        limit = 24,
                    )
                    if (upcomingHourly.isNotEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0x99071732),
                                border = BorderStroke(1.dp, Color(0x4460A5FA)),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Hourly Forecast (Next 24h)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                    )
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
                                                modifier = Modifier.width(66.dp),
                                            ) {
                                                Text(
                                                    text = WeatherUnits.formatIsoTime(h.timeIso, preferences.use24Hour),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFFBFD7FF),
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Icon(
                                                    imageVector = weatherCodeIcon(h.weatherCode),
                                                    contentDescription = null,
                                                    tint = weatherCodeTint(h.weatherCode),
                                                    modifier = Modifier.size(20.dp),
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = WeatherUnits.formatTempShort(h.tempC, preferences.useFahrenheit),
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White,
                                                )
                                                Text(
                                                    text = "${h.rainProbPercent}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF38BDF8),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherGlassMetricTile(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0x8C081B38),
        border = BorderStroke(1.dp, Color(0x4460A5FA)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFBFD7FF),
                maxLines = 1,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SmallTelemetryTag(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF93C5FD),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
    }
}

@Composable
private fun WeatherHeroGraphic(weatherCode: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val hasSun = weatherCode in listOf(0, 1, 2)
        val hasRain = weatherCode in listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)

        if (hasSun) {
            val sunCenter = Offset(w * 0.38f, h * 0.36f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0x66FBBF24), Color.Transparent),
                    center = sunCenter,
                    radius = w * 0.38f,
                ),
                radius = w * 0.38f,
                center = sunCenter,
            )
            drawCircle(
                brush = Brush.linearGradient(listOf(Color(0xFFFDE047), Color(0xFFF59E0B))),
                radius = w * 0.21f,
                center = sunCenter,
            )
        }

        if (weatherCode != 0) {
            // Soft luminous cloud body
            drawCircle(
                color = Color.White.copy(alpha = 0.94f),
                radius = w * 0.19f,
                center = Offset(w * 0.48f, h * 0.56f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.96f),
                radius = w * 0.23f,
                center = Offset(w * 0.62f, h * 0.48f),
            )
            drawCircle(
                color = Color(0xFFE0ECFF),
                radius = w * 0.16f,
                center = Offset(w * 0.77f, h * 0.59f),
            )
            drawRoundRect(
                color = Color(0xFFF0F6FF),
                topLeft = Offset(w * 0.34f, h * 0.54f),
                size = androidx.compose.ui.geometry.Size(w * 0.52f, h * 0.21f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f, w * 0.12f),
            )
        }

        if (hasRain) {
            for (i in 0..2) {
                val rx = w * (0.42f + i * 0.15f)
                drawLine(
                    color = Color(0xFF38BDF8),
                    start = Offset(rx, h * 0.80f),
                    end = Offset(rx - w * 0.04f, h * 0.92f),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun weatherCodeIcon(code: Int): ImageVector = when {
    code == 0 || code == 1 -> Icons.Rounded.WbSunny
    code == 2 -> Icons.Rounded.WbCloudy
    code in listOf(95, 96, 99) -> Icons.Rounded.Thunderstorm
    code in listOf(71, 73, 75, 77, 85, 86) -> Icons.Rounded.AcUnit
    code in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82) -> Icons.Rounded.WaterDrop
    else -> Icons.Rounded.Cloud
}

private fun weatherCodeTint(code: Int): Color = when {
    code == 0 || code == 1 || code == 2 -> Color(0xFFFBBF24)
    code in listOf(95, 96, 99) -> Color(0xFFA78BFA)
    code in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82) -> Color(0xFF38BDF8)
    else -> Color(0xFFBFD7FF)
}

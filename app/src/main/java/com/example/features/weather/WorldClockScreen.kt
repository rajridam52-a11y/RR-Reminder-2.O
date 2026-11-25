package com.example.features.weather

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.database.WorldClockCityEntity
import com.example.core.datastore.UserPreferences
import com.example.core.network.NetworkTimeSyncStatus
import com.example.ui.components.EarthHorizonCanvas
import com.example.ui.components.RRSearchField
import com.example.ui.theme.TabularClockMedium
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WorldClockTabContent(
    cities: List<WorldClockCityEntity>,
    availableToAdd: List<BuiltInCityOption>,
    zoneQuery: String,
    nowMillis: Long,
    preferences: UserPreferences,
    timeSyncStatus: NetworkTimeSyncStatus = NetworkTimeSyncStatus(),
    onSyncNetworkTime: () -> Unit = {},
    onZoneQueryChange: (String) -> Unit,
    onAddCity: (BuiltInCityOption) -> Unit,
    onRemoveCity: (Long) -> Unit,
) {
    var showAddSheet by remember { mutableStateOf(false) }

    val utcZdt = Instant.ofEpochMilli(nowMillis).atZone(ZoneOffset.UTC)
    val indiaZdt = Instant.ofEpochMilli(nowMillis).atZone(com.example.core.notifications.ScheduleCalculator.INDIA_ZONE)
    val isIndiaDay = indiaZdt.hour in 6..18
    val timePattern = if (preferences.use24Hour) "HH:mm" else "hh:mm a"
    val timeFormatter = DateTimeFormatter.ofPattern(timePattern, Locale.getDefault())
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, ${preferences.dateFormatStyle.pattern}", Locale.getDefault())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF030816),
                        Color(0xFF071330),
                        Color(0xFF0A1D42),
                        Color(0xFF040A18),
                    ),
                ),
            ),
    ) {
        EarthHorizonCanvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .align(Alignment.BottomCenter),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Sub-header row with Sync + Add City
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0x660F244D),
                    border = BorderStroke(1.dp, Color(0x4438BDF8)),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onSyncNetworkTime)
                        .testTag("worldclock_sync_time_btn"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Sync,
                            contentDescription = "Sync real time",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = if (timeSyncStatus.isSynced) timeSyncStatus.sourceLabel else "Google Sync",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1D4ED8),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable { showAddSheet = true }
                        .testTag("worldclock_add_fab"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add World City", tint = Color.White)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
            ) {
                // India IST pinned master card
                item {
                    WorldClockGlassCard(
                        cityName = "India (IST)",
                        countryCode = "IN",
                        timeText = indiaZdt.format(timeFormatter),
                        dateText = "${indiaZdt.format(dateFormatter)} • UTC+05:30",
                        isDaytime = isIndiaDay,
                        isUtcCloud = false,
                        modifier = Modifier.testTag("worldclock_india_card"),
                    )
                }

                items(cities, key = { it.id }) { city ->
                    val zone = runCatching { ZoneId.of(city.zoneId) }.getOrDefault(ZoneId.systemDefault())
                    val cityZdt = Instant.ofEpochMilli(nowMillis).atZone(zone)
                    val isDaytime = cityZdt.hour in 6..18
                    val code = resolveCountryCode(city.cityName, city.countryName, city.zoneId)
                    WorldClockGlassCard(
                        cityName = city.cityName,
                        countryCode = code,
                        timeText = cityZdt.format(timeFormatter),
                        dateText = cityZdt.format(dateFormatter),
                        isDaytime = isDaytime,
                        isUtcCloud = city.cityName.contains("London", ignoreCase = true),
                        onRemove = { onRemoveCity(city.id) },
                    )
                }

                // UTC Reference card
                item {
                    WorldClockGlassCard(
                        cityName = "UTC Master",
                        countryCode = "UTC",
                        timeText = utcZdt.format(timeFormatter),
                        dateText = utcZdt.format(dateFormatter),
                        isDaytime = utcZdt.hour in 6..18,
                        isUtcCloud = true,
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AlertDialog(
            onDismissRequest = { showAddSheet = false },
            title = { Text("Add World Clock City") },
            text = {
                Column(modifier = Modifier.heightIn(max = 420.dp)) {
                    RRSearchField(
                        query = zoneQuery,
                        onQueryChange = onZoneQueryChange,
                        placeholder = "Search city, country, or timezone...",
                        testTag = "worldclock_search_input",
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(availableToAdd) { opt ->
                            TextButton(
                                onClick = {
                                    onAddCity(opt)
                                    showAddSheet = false
                                    onZoneQueryChange("")
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(opt.cityName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${opt.countryName} (${opt.zoneId})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddSheet = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun WorldClockGlassCard(
    cityName: String,
    countryCode: String,
    timeText: String,
    dateText: String,
    isDaytime: Boolean,
    isUtcCloud: Boolean,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xCC0D1A38),
        border = BorderStroke(1.dp, Color(0x4460A5FA)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularCountryFlagBadge(code = countryCode)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cityName,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFBFD7FF),
                )
                Text(
                    text = timeText,
                    style = TabularClockMedium.copy(
                        fontSize = MaterialTheme.typography.titleLarge.fontSize,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                )
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF90A7D4),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = when {
                        isUtcCloud && isDaytime -> Icons.Rounded.Cloud
                        isDaytime -> Icons.Rounded.WbSunny
                        else -> Icons.Rounded.NightsStay
                    },
                    contentDescription = if (isDaytime) "Daytime" else "Nighttime",
                    tint = when {
                        isUtcCloud && isDaytime -> Color(0xFFE2E8F0)
                        isDaytime -> Color(0xFFFBBF24)
                        else -> Color(0xFF93C5FD)
                    },
                    modifier = Modifier.size(28.dp),
                )
                if (onRemove != null) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Remove $cityName",
                            tint = Color(0xFF90A7D4),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularCountryFlagBadge(code: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            when (code) {
                "IN" -> {
                    drawRect(Color(0xFFFF9933), topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(w, h / 3f))
                    drawRect(Color.White, topLeft = Offset(0f, h / 3f), size = androidx.compose.ui.geometry.Size(w, h / 3f))
                    drawRect(Color(0xFF138808), topLeft = Offset(0f, h * 2f / 3f), size = androidx.compose.ui.geometry.Size(w, h / 3f))
                    drawCircle(Color(0xFF000080), radius = w * 0.10f, center = Offset(w / 2f, h / 2f), style = Stroke(width = 2.dp.toPx()))
                }
                "US" -> {
                    for (i in 0 until 7) {
                        val stripeColor = if (i % 2 == 0) Color(0xFFB22234) else Color.White
                        drawRect(stripeColor, topLeft = Offset(0f, (h / 7f) * i), size = androidx.compose.ui.geometry.Size(w, h / 7f + 1f))
                    }
                    drawRect(Color(0xFF3C3B6E), topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(w * 0.50f, h * 0.54f))
                }
                "UK" -> {
                    drawRect(Color(0xFF012169))
                    drawLine(Color.White, Offset(0f, 0f), Offset(w, h), strokeWidth = w * 0.16f)
                    drawLine(Color.White, Offset(w, 0f), Offset(0f, h), strokeWidth = w * 0.16f)
                    drawRect(Color.White, Offset(w * 0.38f, 0f), androidx.compose.ui.geometry.Size(w * 0.24f, h))
                    drawRect(Color.White, Offset(0f, h * 0.38f), androidx.compose.ui.geometry.Size(w, h * 0.24f))
                    drawRect(Color(0xFFC8102E), Offset(w * 0.43f, 0f), androidx.compose.ui.geometry.Size(w * 0.14f, h))
                    drawRect(Color(0xFFC8102E), Offset(0f, h * 0.43f), androidx.compose.ui.geometry.Size(w, h * 0.14f))
                }
                "JP" -> {
                    drawRect(Color.White)
                    drawCircle(Color(0xFFBC002D), radius = w * 0.26f, center = Offset(w / 2f, h / 2f))
                }
                else -> {
                    drawRect(
                        brush = Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF38BDF8))),
                    )
                }
            }
            drawCircle(
                color = Color.White.copy(alpha = 0.25f),
                radius = w / 2f,
                center = Offset(w / 2f, h / 2f),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        if (code !in listOf("IN", "US", "UK", "JP")) {
            Text(
                text = code.take(3),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

private fun resolveCountryCode(cityName: String, countryName: String, zoneId: String): String {
    val c = "$cityName $countryName $zoneId".lowercase()
    return when {
        "india" in c || "kolkata" in c || "delhi" in c || "mumbai" in c || "bengaluru" in c -> "IN"
        "new york" in c || "los angeles" in c || "chicago" in c || "united states" in c -> "US"
        "london" in c || "united kingdom" in c -> "UK"
        "tokyo" in c || "japan" in c -> "JP"
        "utc" in c -> "UTC"
        else -> countryName.take(2).uppercase().ifBlank { "GL" }
    }
}

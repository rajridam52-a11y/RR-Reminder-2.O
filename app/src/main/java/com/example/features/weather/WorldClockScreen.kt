package com.example.features.weather

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.database.WorldClockCityEntity
import com.example.core.datastore.UserPreferences
import com.example.core.network.NetworkTimeSyncStatus
import com.example.ui.components.RRCard
import com.example.ui.components.RRSearchField
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.LocalRRExtraColors
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
    val extra = LocalRRExtraColors.current
    var showAddSheet by remember { mutableStateOf(false) }

    val utcZdt = Instant.ofEpochMilli(nowMillis).atZone(ZoneOffset.UTC)
    val indiaZdt = Instant.ofEpochMilli(nowMillis).atZone(com.example.core.notifications.ScheduleCalculator.INDIA_ZONE)
    val isIndiaDay = indiaZdt.hour in 6..18
    val timePattern = if (preferences.use24Hour) "HH:mm:ss" else "h:mm:ss a"
    val timeFormatter = DateTimeFormatter.ofPattern(timePattern, Locale.getDefault())
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, ${preferences.dateFormatStyle.pattern}", Locale.getDefault())
    val fullDateFormatter = DateTimeFormatter.ofPattern("EEEE, ${preferences.dateFormatStyle.pattern}", Locale.getDefault())

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
        ) {
            item {
                RRCard(
                    accentGlow = true,
                    modifier = Modifier.testTag("worldclock_india_card"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "INDIA STANDARD TIME (IST)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = extra.accentPrimary,
                                )
                                RRStatusPill(
                                    text = if (isIndiaDay) "Day" else "Night",
                                    color = if (isIndiaDay) extra.warning else extra.accentSecondary,
                                    icon = if (isIndiaDay) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(indiaZdt.format(timeFormatter), style = TabularClockMedium)
                            Text(
                                text = "${indiaZdt.format(fullDateFormatter)} • UTC+05:30 • ${if (timeSyncStatus.isSynced) timeSyncStatus.sourceLabel else "Device Clock"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = onSyncNetworkTime,
                            modifier = Modifier.testTag("worldclock_sync_time_btn"),
                        ) {
                            Icon(
                                Icons.Rounded.Sync,
                                contentDescription = "Sync real time with Google",
                                tint = extra.accentPrimary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
            }

            item {
                RRCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("COORDINATED UNIVERSAL TIME (UTC)", style = MaterialTheme.typography.labelMedium, color = extra.accentSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(utcZdt.format(timeFormatter), style = TabularClockMedium)
                            Text(
                                utcZdt.format(dateFormatter),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Rounded.Public,
                            contentDescription = "UTC Master Clock",
                            tint = extra.accentSecondary,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
            }

            items(cities, key = { it.id }) { city ->
                val zone = runCatching { ZoneId.of(city.zoneId) }.getOrDefault(ZoneId.systemDefault())
                val cityZdt = Instant.ofEpochMilli(nowMillis).atZone(zone)
                val isDaytime = cityZdt.hour in 6..18
                val offset = cityZdt.offset.id.let { if (it == "Z") "UTC+0" else "UTC$it" }

                RRCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(city.cityName, style = MaterialTheme.typography.titleMedium)
                                RRStatusPill(
                                    text = if (isDaytime) "Day" else "Night",
                                    color = if (isDaytime) extra.warning else extra.accentSecondary,
                                    icon = if (isDaytime) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay,
                                )
                            }
                            Text(
                                text = "${city.countryName} • $offset",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = cityZdt.format(dateFormatter),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = cityZdt.format(timeFormatter),
                            style = TabularClockMedium,
                        )
                        IconButton(onClick = { onRemoveCity(city.id) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove ${city.cityName}")
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("worldclock_add_fab"),
        ) {
            Icon(Icons.Rounded.AddLocationAlt, contentDescription = "Add World City")
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

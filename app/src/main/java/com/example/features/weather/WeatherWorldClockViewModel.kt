package com.example.features.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.database.WeatherCacheEntity
import com.example.core.database.WorldClockCityEntity
import com.example.core.di.AppContainer
import com.example.core.location.LocationFetchResult
import com.example.core.network.DailyForecastItem
import com.example.core.network.GeocodingResultDto
import com.example.core.network.HourlyForecastItem
import com.example.core.network.NetworkTimeSyncStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId

data class WeatherUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isShowingCachedData: Boolean = false,
    val isGpsDisabled: Boolean = false,
    val isGpsSourced: Boolean = false,
    val gpsProviderInfo: String? = null,
    val cache: WeatherCacheEntity? = null,
    val hourly: List<HourlyForecastItem> = emptyList(),
    val daily: List<DailyForecastItem> = emptyList(),
    val citySearchResults: List<GeocodingResultDto> = emptyList(),
    val isSearchingCities: Boolean = false,
)

data class BuiltInCityOption(
    val cityName: String,
    val countryName: String,
    val zoneId: String,
)

class WeatherWorldClockViewModel(private val container: AppContainer) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isShowingCachedData = MutableStateFlow(false)
    private val _isGpsDisabled = MutableStateFlow(false)
    private val _isGpsSourced = MutableStateFlow(false)
    private val _gpsProviderInfo = MutableStateFlow<String?>(null)
    private val _cityResults = MutableStateFlow<List<GeocodingResultDto>>(emptyList())
    private val _isSearching = MutableStateFlow(false)

    val weatherUiState: StateFlow<WeatherUiState> = combine(
        combine(_isLoading, _errorMessage, _isShowingCachedData, _isGpsDisabled, _isGpsSourced) { loading, err, cached, gpsOff, gpsSrc ->
            Tuple5(loading, err, cached, gpsOff, gpsSrc)
        },
        _gpsProviderInfo,
        container.weatherRepository.weatherCache,
        _cityResults,
        _isSearching,
    ) { status, gpsInfo, cache, cities, searching ->
        WeatherUiState(
            isLoading = status.first,
            errorMessage = status.second,
            isShowingCachedData = status.third && cache != null,
            isGpsDisabled = status.fourth,
            isGpsSourced = status.fifth,
            gpsProviderInfo = gpsInfo,
            cache = cache,
            hourly = cache?.let { container.weatherRepository.parseHourly(it.hourlyJson) }.orEmpty(),
            daily = cache?.let { container.weatherRepository.parseDaily(it.dailyJson) }.orEmpty(),
            citySearchResults = cities,
            isSearchingCities = searching,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeatherUiState())

    val worldCities: StateFlow<List<WorldClockCityEntity>> =
        container.database.worldClockDao().observeCities()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _nowMillis = MutableStateFlow(container.networkTimeProvider.currentEpochMillis())
    val nowMillis: StateFlow<Long> = _nowMillis.asStateFlow()

    val timeSyncStatus: StateFlow<NetworkTimeSyncStatus> = container.networkTimeProvider.syncStatus

    private val _zoneQuery = MutableStateFlow("")
    val zoneQuery: StateFlow<String> = _zoneQuery.asStateFlow()

    val availableCitiesToAdd: StateFlow<List<BuiltInCityOption>> = _zoneQuery.map { q ->
        val trimmed = q.trim().lowercase()
        val all = curatedAndSystemZones
        if (trimmed.isBlank()) all.take(30)
        else all.filter {
            it.cityName.lowercase().contains(trimmed) ||
                it.countryName.lowercase().contains(trimmed) ||
                it.zoneId.lowercase().contains(trimmed)
        }.take(40)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), curatedAndSystemZones.take(30))

    init {
        viewModelScope.launch {
            while (isActive) {
                val now = container.networkTimeProvider.currentEpochMillis()
                _nowMillis.value = now
                val waitMs = (1000L - (now % 1000L)).coerceAtLeast(50L) + 10L
                delay(waitMs)
            }
        }
        viewModelScope.launch {
            val prefs = container.preferencesRepository.getSnapshot()
            val cache = container.database.weatherCacheDao().getCacheSync()
            val isStale = cache == null || (System.currentTimeMillis() - cache.updatedAtMillis) > STALE_CACHE_MS
            if (container.locationHelper.hasLocationPermission() &&
                container.locationHelper.isGpsEnabled() &&
                !prefs.hasSavedWeatherLocation
            ) {
                fetchWeatherByCurrentLocation()
            } else if (prefs.hasSavedWeatherLocation && isStale) {
                refreshSavedWeather()
            }
        }
    }

    fun syncNetworkTime() {
        viewModelScope.launch {
            container.networkTimeProvider.syncNow()
            _nowMillis.value = container.networkTimeProvider.currentEpochMillis()
        }
    }

    fun refreshClockNow() {
        _nowMillis.value = container.networkTimeProvider.currentEpochMillis()
    }

    fun refreshSavedWeather() {
        viewModelScope.launch {
            val prefs = container.preferencesRepository.getSnapshot()
            if (!prefs.hasSavedWeatherLocation) {
                if (container.locationHelper.hasLocationPermission()) {
                    fetchWeatherByCurrentLocation()
                } else {
                    _errorMessage.value = "Search a city or grant GPS access to load live weather."
                }
                return@launch
            }
            fetchWeatherForCity(
                cityName = prefs.savedWeatherCity,
                lat = prefs.savedWeatherLat,
                lon = prefs.savedWeatherLon,
                fromGps = false,
            )
        }
    }

    fun fetchWeatherByCurrentLocation() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _isShowingCachedData.value = false
            _isGpsDisabled.value = false
            when (val result = container.locationHelper.getCurrentLocationDetailed()) {
                is LocationFetchResult.Success -> {
                    val loc = result.location
                    val providerLabel = buildString {
                        append(loc.provider?.uppercase() ?: "GPS")
                        loc.accuracyMeters?.let { append(" (±${it.toInt()}m)") }
                    }
                    _gpsProviderInfo.value = providerLabel
                    fetchWeatherForCity(
                        cityName = loc.displayName,
                        lat = loc.latitude,
                        lon = loc.longitude,
                        fromGps = true,
                    )
                }
                is LocationFetchResult.PermissionDenied -> {
                    _isLoading.value = false
                    _errorMessage.value = "Location permission is required to read your real-time GPS forecast."
                }
                is LocationFetchResult.GpsDisabled -> {
                    _isLoading.value = false
                    _isGpsDisabled.value = true
                    _errorMessage.value = "Device GPS / Location Services are turned off. Turn on Location to fetch live local weather."
                }
                is LocationFetchResult.Unavailable -> {
                    _isLoading.value = false
                    _errorMessage.value = result.reason
                }
            }
        }
    }

    fun fetchWeatherForCity(
        cityName: String,
        lat: Double,
        lon: Double,
        fromGps: Boolean = false,
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _isShowingCachedData.value = false
            _isGpsDisabled.value = false
            _isGpsSourced.value = fromGps
            if (!fromGps) _gpsProviderInfo.value = null
            val result = container.weatherRepository.fetchAndCacheWeather(cityName, lat, lon)
            _isLoading.value = false
            result.onFailure { ex ->
                _isShowingCachedData.value = true
                _errorMessage.value = "Offline or unable to reach Open-Meteo (${ex.localizedMessage ?: "Network error"})."
            }
        }
    }

    fun searchWeatherCities(query: String) {
        viewModelScope.launch {
            if (query.trim().length < 2) {
                _cityResults.value = emptyList()
                return@launch
            }
            _isSearching.value = true
            val res = container.weatherRepository.searchCities(query)
            _isSearching.value = false
            res.onSuccess { _cityResults.value = it }
                .onFailure { _errorMessage.value = "City search failed: ${it.localizedMessage ?: "Network unavailable"}" }
        }
    }

    fun clearCityResults() {
        _cityResults.value = emptyList()
    }

    fun setZoneSearchQuery(q: String) {
        _zoneQuery.value = q
    }

    fun addWorldCity(option: BuiltInCityOption) {
        viewModelScope.launch {
            val existing = container.database.worldClockDao().getAllSync()
            if (existing.none { it.zoneId == option.zoneId && it.cityName.equals(option.cityName, ignoreCase = true) }) {
                container.database.worldClockDao().insertCity(
                    WorldClockCityEntity(
                        cityName = option.cityName,
                        countryName = option.countryName,
                        zoneId = option.zoneId,
                        sortOrder = existing.size,
                    ),
                )
            }
        }
    }

    fun removeWorldCity(id: Long) {
        viewModelScope.launch { container.database.worldClockDao().deleteCityById(id) }
    }

    private data class Tuple5<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
    )

    companion object {
        private const val STALE_CACHE_MS = 30 * 60_000L

        private val curatedAndSystemZones: List<BuiltInCityOption> by lazy {
            val curated = listOf(
                BuiltInCityOption("New York", "United States", "America/New_York"),
                BuiltInCityOption("Los Angeles", "United States", "America/Los_Angeles"),
                BuiltInCityOption("Chicago", "United States", "America/Chicago"),
                BuiltInCityOption("Toronto", "Canada", "America/Toronto"),
                BuiltInCityOption("São Paulo", "Brazil", "America/Sao_Paulo"),
                BuiltInCityOption("London", "United Kingdom", "Europe/London"),
                BuiltInCityOption("Paris", "France", "Europe/Paris"),
                BuiltInCityOption("Berlin", "Germany", "Europe/Berlin"),
                BuiltInCityOption("Zurich", "Switzerland", "Europe/Zurich"),
                BuiltInCityOption("Dubai", "UAE", "Asia/Dubai"),
                BuiltInCityOption("New Delhi", "India", "Asia/Kolkata"),
                BuiltInCityOption("Mumbai", "India", "Asia/Kolkata"),
                BuiltInCityOption("Bengaluru", "India", "Asia/Kolkata"),
                BuiltInCityOption("Singapore", "Singapore", "Asia/Singapore"),
                BuiltInCityOption("Tokyo", "Japan", "Asia/Tokyo"),
                BuiltInCityOption("Seoul", "South Korea", "Asia/Seoul"),
                BuiltInCityOption("Sydney", "Australia", "Australia/Sydney"),
                BuiltInCityOption("Auckland", "New Zealand", "Pacific/Auckland"),
                BuiltInCityOption("Johannesburg", "South Africa", "Africa/Johannesburg"),
                BuiltInCityOption("Cairo", "Egypt", "Africa/Cairo"),
            )
            val curatedIds = curated.map { it.zoneId }.toSet()
            val extra = ZoneId.getAvailableZoneIds()
                .filter { "/" in it && !it.startsWith("Etc/") && it !in curatedIds }
                .sorted()
                .map { id ->
                    val parts = id.split("/")
                    val city = parts.last().replace("_", " ")
                    val region = parts.first().replace("_", " ")
                    BuiltInCityOption(city, region, id)
                }
            curated + extra
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WeatherWorldClockViewModel(container) as T
    }
}

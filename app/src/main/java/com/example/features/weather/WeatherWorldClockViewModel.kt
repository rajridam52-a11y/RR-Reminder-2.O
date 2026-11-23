package com.example.features.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.database.WeatherCacheEntity
import com.example.core.database.WorldClockCityEntity
import com.example.core.di.AppContainer
import com.example.core.network.DailyForecastItem
import com.example.core.network.GeocodingResultDto
import com.example.core.network.HourlyForecastItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.ZoneId

data class WeatherUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
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
    private val _cityResults = MutableStateFlow<List<GeocodingResultDto>>(emptyList())
    private val _isSearching = MutableStateFlow(false)

    val weatherUiState: StateFlow<WeatherUiState> = combine(
        _isLoading,
        _errorMessage,
        container.weatherRepository.weatherCache,
        _cityResults,
        _isSearching,
    ) { loading, err, cache, cities, searching ->
        WeatherUiState(
            isLoading = loading,
            errorMessage = err,
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

    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())
    val nowMillis: StateFlow<Long> = _nowMillis.asStateFlow()

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
            while (true) {
                _nowMillis.value = System.currentTimeMillis()
                delay(1000L)
            }
        }
        viewModelScope.launch {
            val prefs = container.preferencesRepository.getSnapshot()
            if (prefs.hasSavedWeatherLocation) {
                refreshSavedWeather()
            }
        }
    }

    fun refreshSavedWeather() {
        viewModelScope.launch {
            val prefs = container.preferencesRepository.getSnapshot()
            if (!prefs.hasSavedWeatherLocation) {
                _errorMessage.value = "Select a city or grant location access to load live weather."
                return@launch
            }
            fetchWeatherForCity(prefs.savedWeatherCity, prefs.savedWeatherLat, prefs.savedWeatherLon)
        }
    }

    fun fetchWeatherByCurrentLocation() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val loc = container.locationHelper.getCurrentLocation()
            if (loc == null) {
                _isLoading.value = false
                _errorMessage.value = "Could not obtain device location. Please search and select a city manually."
                return@launch
            }
            fetchWeatherForCity(loc.displayName, loc.latitude, loc.longitude)
        }
    }

    fun fetchWeatherForCity(cityName: String, lat: Double, lon: Double) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = container.weatherRepository.fetchAndCacheWeather(cityName, lat, lon)
            _isLoading.value = false
            result.onFailure { ex ->
                _errorMessage.value = "Unable to reach weather service (${ex.localizedMessage ?: "Network error"}). Showing last cached data if available."
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
            if (existing.none { it.zoneId == option.zoneId }) {
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

    companion object {
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

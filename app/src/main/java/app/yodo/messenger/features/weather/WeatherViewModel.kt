package app.yodo.messenger.features.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.data.local.UserSettingsPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

sealed class WeatherUiState {
    data object Loading : WeatherUiState()
    data class Ready(val data: WeatherBundle) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val repository: WeatherRepository,
    private val preferences: UserSettingsPreferences
) : ViewModel() {
    val city: StateFlow<String> = preferences.weatherCity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Санкт-Петербург")
    val weatherCardEnabled: StateFlow<Boolean> = preferences.weatherCardEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState

    init {
        viewModelScope.launch {
            preferences.weatherCity.distinctUntilChanged().collectLatest { load(it, force = false) }
        }
    }

    fun refresh() = load(city.value, force = true)

    fun selectCity(value: String) {
        val normalized = value.trim()
        if (normalized.length < 2) return
        viewModelScope.launch { preferences.setWeatherCity(normalized) }
    }

    fun setWeatherCardEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setWeatherCardEnabled(enabled) }
    }

    private fun load(city: String, force: Boolean) {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            _uiState.value = try {
                WeatherUiState.Ready(repository.load(city, force))
            } catch (error: Throwable) {
                WeatherUiState.Error(
                    when (error) {
                        is HttpException -> when (error.code()) {
                            401 -> "Ключ OpenWeatherMap не принят"
                            404 -> "Город не найден"
                            429 -> "Слишком много запросов. Попробуйте позже"
                            else -> "Ошибка сервиса погоды: ${error.code()}"
                        }
                        else -> error.message ?: "Не удалось загрузить погоду"
                    }
                )
            }
        }
    }
}

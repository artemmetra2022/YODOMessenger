package app.yodo.messenger.features.nearby

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.NearbyPerson
import app.yodo.messenger.domain.repository.NearbyPeopleRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed class NearbyUiState {
    data object Loading : NearbyUiState()
    data object LocationUnavailable : NearbyUiState()
    // НОВОЕ (баг 13): для информативности UI добавлены точность геолокации, радиус
    // поиска и время последнего обновления — показываются в шапке экрана и на карте.
    data class Content(
        val people: List<NearbyPerson>,
        val myLat: Double,
        val myLng: Double,
        val myAccuracyMeters: Float = 0f,
        val radiusKm: Double = 10.0,
        val searchedAtMillis: Long = 0L,
        val isRefreshing: Boolean = false,
        val refreshFailed: Boolean = false
    ) : NearbyUiState()
}

@HiltViewModel
class NearbyPeopleViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nearbyPeopleRepository: NearbyPeopleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<NearbyUiState>(NearbyUiState.Loading)
    val uiState: StateFlow<NearbyUiState> = _uiState

    private val radiusKm = 10.0
    private var searchJob: Job? = null

    @SuppressLint("MissingPermission") // разрешение проверяется в Compose-слое перед вызовом
    fun startSearching() {
        // Keep existing results and the MapView alive during refresh.
        val previous = _uiState.value as? NearbyUiState.Content
        searchJob?.cancel()
        _uiState.value = previous?.copy(isRefreshing = true, refreshFailed = false) ?: NearbyUiState.Loading
        searchJob = viewModelScope.launch {
            try {
                val result = withTimeout(25_000L) {
                    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                    val location = fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                        ?: error("Location unavailable")
                    check(validNearbyCoordinates(location.latitude, location.longitude))
                    coroutineContext.ensureActive()
                    nearbyPeopleRepository.updateMyLocation(location.latitude, location.longitude)
                    coroutineContext.ensureActive()
                    val people = nearbyPeopleRepository.findNearbyPeople(location.latitude, location.longitude, radiusKm)
                    NearbyUiState.Content(
                        people = people.filter { validNearbyCoordinates(it.latitude, it.longitude) },
                        myLat = location.latitude,
                        myLng = location.longitude,
                        myAccuracyMeters = location.accuracy,
                        radiusKm = radiusKm,
                        searchedAtMillis = System.currentTimeMillis()
                    )
                }
                _uiState.value = result
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _uiState.value = previous?.copy(isRefreshing = false, refreshFailed = true) ?: NearbyUiState.LocationUnavailable
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = previous?.copy(isRefreshing = false, refreshFailed = true) ?: NearbyUiState.LocationUnavailable
            }
        }
    }

    fun stopSharingLocation() {
        searchJob?.cancel()
        searchJob = null
        // ИСПРАВЛЕНО (баг 14): fire-and-forget без viewModelScope — очистка гарантированно
        // уходит в Firestore при выходе с экрана, а не зависит от жизненного цикла корутин.
        nearbyPeopleRepository.clearMyLocationNow()
    }

    override fun onCleared() {
        super.onCleared()
        // По умолчанию не публикуем геопозицию дольше, чем экран открыт — приватность прежде всего.
        // ИСПРАВЛЕНО (баг 14): раньше запуск корутины в уже отменённый viewModelScope —
        // очистка из onCleared() молча не выполнялась. Теперь вызываем напрямую.
        nearbyPeopleRepository.clearMyLocationNow()
    }
}

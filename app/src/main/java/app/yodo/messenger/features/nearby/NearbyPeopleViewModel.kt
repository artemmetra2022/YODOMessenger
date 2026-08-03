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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed class NearbyUiState {
    data object Loading : NearbyUiState()
    data object LocationUnavailable : NearbyUiState()
    data class Content(val people: List<NearbyPerson>, val myLat: Double, val myLng: Double) : NearbyUiState()
}

@HiltViewModel
class NearbyPeopleViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nearbyPeopleRepository: NearbyPeopleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<NearbyUiState>(NearbyUiState.Loading)
    val uiState: StateFlow<NearbyUiState> = _uiState

    private val radiusKm = 10.0

    @SuppressLint("MissingPermission") // разрешение проверяется в Compose-слое перед вызовом
    fun startSearching() {
        _uiState.value = NearbyUiState.Loading
        viewModelScope.launch {
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                val location = fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()

                if (location == null) {
                    _uiState.value = NearbyUiState.LocationUnavailable
                    return@launch
                }

                nearbyPeopleRepository.updateMyLocation(location.latitude, location.longitude)
                val people = nearbyPeopleRepository.findNearbyPeople(location.latitude, location.longitude, radiusKm)

                _uiState.value = NearbyUiState.Content(
                    people = people,
                    myLat = location.latitude,
                    myLng = location.longitude
                )
            } catch (e: Exception) {
                _uiState.value = NearbyUiState.LocationUnavailable
            }
        }
    }

    fun stopSharingLocation() {
        viewModelScope.launch { nearbyPeopleRepository.clearMyLocation() }
    }

    override fun onCleared() {
        super.onCleared()
        // По умолчанию не публикуем геопозицию дольше, чем экран открыт — приватность прежде всего
        viewModelScope.launch { nearbyPeopleRepository.clearMyLocation() }
    }
}

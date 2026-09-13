package app.yodo.messenger.features.weather

import app.yodo.messenger.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class WeatherCondition(
    val id: Int = 800,
    val main: String = "",
    val description: String = ""
)

@Serializable
data class WeatherMain(
    val temp: Double = 0.0,
    @SerialName("feels_like") val feelsLike: Double = 0.0,
    @SerialName("temp_min") val tempMin: Double = 0.0,
    @SerialName("temp_max") val tempMax: Double = 0.0,
    val pressure: Int = 0,
    val humidity: Int = 0
)

@Serializable
data class WeatherWind(val speed: Double = 0.0)

@Serializable
data class CurrentWeatherResponse(
    val name: String = "",
    val weather: List<WeatherCondition> = emptyList(),
    val main: WeatherMain = WeatherMain(),
    val wind: WeatherWind = WeatherWind(),
    val dt: Long = 0L
)

@Serializable
data class ForecastItem(
    val dt: Long,
    val main: WeatherMain,
    val weather: List<WeatherCondition> = emptyList()
)

@Serializable
data class ForecastResponse(val list: List<ForecastItem> = emptyList())

data class WeatherBundle(
    val current: CurrentWeatherResponse,
    val forecast: ForecastResponse,
    val loadedAt: Long = System.currentTimeMillis()
)

private interface OpenWeatherApi {
    @GET("data/2.5/weather")
    suspend fun current(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") language: String = "ru"
    ): CurrentWeatherResponse

    @GET("data/2.5/forecast")
    suspend fun forecast(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") language: String = "ru"
    ): ForecastResponse
}

@Singleton
class WeatherRepository @Inject constructor() {
    private val api: OpenWeatherApi by lazy {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenWeatherApi::class.java)
    }

    private val cache = ConcurrentHashMap<String, WeatherBundle>()

    suspend fun load(city: String, force: Boolean = false): WeatherBundle {
        val key = BuildConfig.OPENWEATHER_API_KEY
        check(key.isNotBlank()) { "Ключ OpenWeatherMap не настроен" }
        val normalized = city.trim().ifBlank { "Санкт-Петербург" }
        val cacheKey = normalized.lowercase()
        val cached = cache[cacheKey]
        if (!force && cached != null && System.currentTimeMillis() - cached.loadedAt < 30 * 60_000L) {
            return cached
        }
        val bundle = WeatherBundle(
            current = api.current(normalized, key),
            forecast = api.forecast(normalized, key)
        )
        cache[cacheKey] = bundle
        return bundle
    }
}

fun weatherEmoji(code: Int): String = when (code) {
    in 200..232 -> "⛈️"
    in 300..321 -> "🌦️"
    in 500..531 -> "🌧️"
    in 600..622 -> "🌨️"
    in 701..781 -> "🌫️"
    800 -> "☀️"
    801 -> "🌤️"
    802 -> "⛅"
    in 803..804 -> "☁️"
    else -> "🌡️"
}

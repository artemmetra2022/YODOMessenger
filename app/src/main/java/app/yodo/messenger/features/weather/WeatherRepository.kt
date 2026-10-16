package app.yodo.messenger.features.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class WeatherCondition(
    val symbol: String = "cloudy",
    val description: String = ""
)

data class WeatherMain(
    val temp: Double = 0.0,
    val feelsLike: Double = 0.0,
    val tempMin: Double = 0.0,
    val tempMax: Double = 0.0,
    val pressure: Int = 0,
    val humidity: Int = 0
)

data class WeatherWind(val speed: Double = 0.0)

data class CurrentWeatherResponse(
    val name: String = "",
    val weather: List<WeatherCondition> = emptyList(),
    val main: WeatherMain = WeatherMain(),
    val wind: WeatherWind = WeatherWind(),
    val dt: Long = 0L
)

data class ForecastItem(
    val dt: Long,
    val main: WeatherMain,
    val weather: List<WeatherCondition> = emptyList()
)

data class ForecastResponse(val list: List<ForecastItem> = emptyList())

data class WeatherBundle(
    val current: CurrentWeatherResponse,
    val forecast: ForecastResponse,
    val loadedAt: Long = System.currentTimeMillis()
)

class CityNotFoundException(city: String) : Exception("Город «$city» не найден. Проверьте название")

// --- Ответ met.no (locationforecast 2.0, формат JSON-LD) ---

@Serializable
data class MetDetails(
    @SerialName("air_temperature") val airTemperature: Double = 0.0,
    @SerialName("apparent_air_temperature") val apparentTemperature: Double? = null,
    @SerialName("relative_humidity") val relativeHumidity: Double = 0.0,
    @SerialName("air_pressure_at_sea_level") val pressure: Double = 0.0,
    @SerialName("wind_speed") val windSpeed: Double = 0.0,
    @SerialName("air_temperature_min") val temperatureMin: Double? = null,
    @SerialName("air_temperature_max") val temperatureMax: Double? = null
)

@Serializable
data class MetSummary(@SerialName("symbol_code") val symbolCode: String = "")

@Serializable
data class MetBlock(
    val details: MetDetails = MetDetails(),
    val summary: MetSummary? = null
)

@Serializable
data class MetInstant(val details: MetDetails = MetDetails())

@Serializable
data class MetSeriesData(
    val instant: MetInstant = MetInstant(),
    @SerialName("next_1_hours") val next1Hours: MetBlock? = null,
    @SerialName("next_6_hours") val next6Hours: MetBlock? = null,
    @SerialName("next_12_hours") val next12Hours: MetBlock? = null
)

@Serializable
data class MetSeriesEntry(
    val time: String = "",
    val data: MetSeriesData = MetSeriesData()
)

@Serializable
data class MetProperties(val timeseries: List<MetSeriesEntry> = emptyList())

@Serializable
data class MetForecastResponse(val properties: MetProperties = MetProperties())

// --- Ответ Nominatim (OpenStreetMap): название города → координаты ---

@Serializable
data class NominatimPlace(
    val lat: String = "",
    val lon: String = "",
    val name: String = ""
)

// Оба сервиса требуют в запросе строку с названием приложения и контактом —
// этого просит политика met.no и правила использования Nominatim.
private const val WEATHER_USER_AGENT =
    "YODO-Messenger/Android (+https://github.com/artemmetra2022/YODOMessenger)"

private interface MetNoApi {
    @GET("weatherapi/locationforecast/2.0/complete")
    suspend fun forecast(
        @Query("lat") lat: String,
        @Query("lon") lon: String
    ): MetForecastResponse
}

private interface NominatimApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "jsonv2",
        @Query("limit") limit: Int = 1,
        @Query("accept-language") language: String = "ru"
    ): List<NominatimPlace>
}

@Singleton
class WeatherRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", WEATHER_USER_AGENT)
                    .build()
            )
        }
        .build()

    private val metApi: MetNoApi by lazy { createService("https://api.met.no/", MetNoApi::class.java) }
    private val geocoderApi: NominatimApi by lazy {
        createService("https://nominatim.openstreetmap.org/", NominatimApi::class.java)
    }

    private val bundles = ConcurrentHashMap<String, WeatherBundle>()

    // Nominatim просит не чаще одного запроса в секунду, а координаты города не меняются —
    // поэтому результат геокодинга держим в памяти и не спрашиваем повторно.
    private val places = ConcurrentHashMap<String, NominatimPlace>()

    suspend fun load(city: String, force: Boolean = false): WeatherBundle {
        val normalized = city.trim().ifBlank { DEFAULT_CITY }
        val cacheKey = normalized.lowercase(Locale.ROOT)
        val cached = bundles[cacheKey]
        if (!force && cached != null && System.currentTimeMillis() - cached.loadedAt < CACHE_TTL_MS) {
            return cached
        }

        val place = geocode(normalized)
        val entries = metApi
            .forecast(formatCoordinate(place.lat), formatCoordinate(place.lon))
            .properties
            .timeseries
        if (entries.isEmpty()) throw IllegalStateException("Сервис погоды не вернул данные")

        val bundle = WeatherBundle(
            current = entries.first().toCurrentWeather(place.name.trim().ifBlank { normalized }),
            forecast = ForecastResponse(entries.map { it.toForecastItem() })
        )
        bundles[cacheKey] = bundle
        return bundle
    }

    private suspend fun geocode(city: String): NominatimPlace {
        val cacheKey = city.lowercase(Locale.ROOT)
        places[cacheKey]?.let { return it }
        val place = geocoderApi.search(city).firstOrNull() ?: throw CityNotFoundException(city)
        places[cacheKey] = place
        return place
    }

    private fun <T> createService(baseUrl: String, service: Class<T>): T {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(service)
    }

    private companion object {
        const val DEFAULT_CITY = "Санкт-Петербург"
        const val CACHE_TTL_MS = 30 * 60 * 1000L
    }
}

// met.no не принимает координаты точнее четырёх знаков после запятой.
private fun formatCoordinate(raw: String): String =
    raw.toDoubleOrNull()?.let { String.format(Locale.US, "%.4f", it) } ?: raw

private fun MetSeriesEntry.toCurrentWeather(cityName: String): CurrentWeatherResponse {
    val details = data.instant.details
    return CurrentWeatherResponse(
        name = cityName,
        weather = listOf(data.symbolCode().toCondition()),
        main = details.toWeatherMain(),
        wind = WeatherWind(speed = details.windSpeed),
        dt = time.toEpochSeconds()
    )
}

private fun MetSeriesEntry.toForecastItem(): ForecastItem {
    val details = data.instant.details
    return ForecastItem(
        dt = time.toEpochSeconds(),
        main = details.toWeatherMain(sixHour = data.next6Hours?.details),
        weather = listOf(data.symbolCode().toCondition())
    )
}

private fun MetDetails.toWeatherMain(sixHour: MetDetails? = null): WeatherMain = WeatherMain(
    temp = airTemperature,
    feelsLike = apparentTemperature ?: airTemperature,
    tempMin = sixHour?.temperatureMin ?: temperatureMin ?: airTemperature,
    tempMax = sixHour?.temperatureMax ?: temperatureMax ?: airTemperature,
    pressure = pressure.roundToInt(),
    humidity = relativeHumidity.roundToInt()
)

private fun MetSeriesData.symbolCode(): String =
    next1Hours?.summary?.symbolCode?.takeIf { it.isNotBlank() }
        ?: next6Hours?.summary?.symbolCode?.takeIf { it.isNotBlank() }
        ?: next12Hours?.summary?.symbolCode?.takeIf { it.isNotBlank() }
        ?: "cloudy"

private fun String.toCondition(): WeatherCondition =
    WeatherCondition(symbol = this, description = weatherDescription(this))

// met.no отдаёт время в UTC ("2026-10-16T19:00:00Z"); экран сам переводит epoch в местное время.
private fun String.toEpochSeconds(): Long =
    runCatching { Instant.parse(this).epochSecond }.getOrDefault(0L)

fun weatherEmoji(symbol: String): String = when (val base = symbol.baseSymbol()) {
    "clearsky" -> "☀️"
    "fair" -> "🌤️"
    "partlycloudy" -> "⛅"
    "cloudy" -> "☁️"
    "fog" -> "🌫️"
    else -> when {
        base.contains("thunder") -> "⛈️"
        base.contains("snow") || base.contains("sleet") -> "🌨️"
        base.contains("heavyrain") -> "🌧️"
        base.contains("rain") -> "🌦️"
        else -> "🌡️"
    }
}

fun weatherDescription(symbol: String): String = when (val base = symbol.baseSymbol()) {
    "clearsky" -> "ясно"
    "fair" -> "малооблачно"
    "partlycloudy" -> "переменная облачность"
    "cloudy" -> "облачно"
    "fog" -> "туман"
    else -> when {
        base.contains("thunder") -> "гроза"
        base.contains("heavysnow") -> "сильный снег"
        base.contains("snowshowers") -> "снегопад"
        base.contains("snow") -> "снег"
        base.contains("rainshowers") -> "ливень"
        base.contains("heavysleet") -> "сильный мокрый снег"
        base.contains("sleet") -> "мокрый снег"
        base.contains("heavyrain") -> "сильный дождь"
        base.contains("lightrain") -> "небольшой дождь"
        base.contains("rain") -> "дождь"
        else -> "—"
    }
}

// Коды met.no содержат время суток в суффиксе (clearsky_day, partlycloudy_night):
// сами иконки и подписи от времени суток у нас не зависят.
private fun String.baseSymbol(): String {
    val suffix = listOf("_day", "_night", "_polartwilight").firstOrNull { endsWith(it) }
    return if (suffix == null) this else dropLast(suffix.length)
}

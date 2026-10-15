package app.yodo.messenger.features.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.data.local.InterfaceStyle
import app.yodo.messenger.ui.components.liquidGlass
import app.yodo.messenger.ui.components.softMessengerBackdrop
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.LocalInterfaceStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WeatherCompactCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeatherViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val experimental = LocalInterfaceStyle.current == InterfaceStyle.EXPERIMENTAL
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val colorTheme = LocalColorTheme.current
    val shape = RoundedCornerShape(20.dp)
    Surface(
        color = if (experimental) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .liquidGlass(experimental, shape, MaterialTheme.colorScheme.surface, dark, 4)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (val current = state) {
                WeatherUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Обновляем погоду…", modifier = Modifier.weight(1f))
                }
                is WeatherUiState.Error -> {
                    Text("🌡️", fontSize = 28.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Погода недоступна", fontWeight = FontWeight.SemiBold)
                        Text(current.message, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                is WeatherUiState.Ready -> {
                    val weather = current.data.current
                    Text(weatherEmoji(weather.weather.firstOrNull()?.id ?: 800), fontSize = 32.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(weather.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            weather.weather.firstOrNull()?.description.orEmpty().replaceFirstChar { it.titlecase(Locale.getDefault()) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Text("${weather.main.temp.roundToInt()}°", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = colorTheme.primary)
                }
            }
        }
    }
}

private data class ForecastDay(val day: String, val icon: String, val description: String, val min: Int, val max: Int)

@Composable
fun WeatherScreen(
    onBackClick: () -> Unit,
    viewModel: WeatherViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val city by viewModel.city.collectAsState()
    val colorTheme = LocalColorTheme.current
    val experimental = LocalInterfaceStyle.current == InterfaceStyle.EXPERIMENTAL
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var cityInput by remember(city) { mutableStateOf(city) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Погода") },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = { IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, "Обновить") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .softMessengerBackdrop(experimental, colorTheme.primary, colorTheme.accent, dark)
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = cityInput,
                    onValueChange = { cityInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Город") },
                    leadingIcon = { Icon(Icons.Filled.LocationOn, null) },
                    trailingIcon = {
                        IconButton(onClick = { viewModel.selectCity(cityInput) }) {
                            Icon(Icons.Filled.Search, "Найти")
                        }
                    }
                )
            }
            when (val current = state) {
                WeatherUiState.Loading -> item {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                is WeatherUiState.Error -> item {
                    WeatherErrorCard(current.message) { viewModel.refresh() }
                }
                is WeatherUiState.Ready -> {
                    val weather = current.data.current
                    item { CurrentWeatherCard(weather, experimental, dark) }
                    item { Text("Прогноз на 5 дней", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    val formatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale("ru"))
                    val days = current.data.forecast.list
                        .groupBy { Instant.ofEpochSecond(it.dt).atZone(ZoneId.systemDefault()).toLocalDate() }
                        .entries.take(5).map { (date, values) ->
                            val representative = values.minByOrNull { kotlin.math.abs((it.dt % 86400) - 43200) } ?: values.first()
                            ForecastDay(
                                day = date.format(formatter).replaceFirstChar { it.titlecase(Locale("ru")) },
                                icon = weatherEmoji(representative.weather.firstOrNull()?.id ?: 800),
                                description = representative.weather.firstOrNull()?.description.orEmpty(),
                                min = values.minOf { it.main.tempMin }.roundToInt(),
                                max = values.maxOf { it.main.tempMax }.roundToInt()
                            )
                        }
                    items(days) { day -> ForecastDayRow(day, experimental, dark) }
                    item {
                        Text(
                            "Данные предоставлены OpenWeatherMap · обновление кешируется на 30 минут",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentWeatherCard(weather: CurrentWeatherResponse, experimental: Boolean, dark: Boolean) {
    val colorTheme = LocalColorTheme.current
    val shape = RoundedCornerShape(28.dp)
    Surface(
        color = if (experimental) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
        shape = shape,
        modifier = Modifier.fillMaxWidth().liquidGlass(experimental, shape, MaterialTheme.colorScheme.surface, dark, 8)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(weatherEmoji(weather.weather.firstOrNull()?.id ?: 800), fontSize = 54.sp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(weather.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(weather.weather.firstOrNull()?.description.orEmpty().replaceFirstChar { it.titlecase(Locale.getDefault()) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${weather.main.temp.roundToInt()}°", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = colorTheme.primary)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                WeatherMetric("Ощущается", "${weather.main.feelsLike.roundToInt()}°", "🌡️")
                WeatherMetric("Влажность", "${weather.main.humidity}%", "💧")
                WeatherMetric("Ветер", "${weather.wind.speed.roundToInt()} м/с", "💨")
            }
        }
    }
}

@Composable
private fun WeatherMetric(label: String, value: String, emoji: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 20.sp)
        Text(value, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ForecastDayRow(day: ForecastDay, experimental: Boolean, dark: Boolean) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        color = if (experimental) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
        shape = shape,
        modifier = Modifier.fillMaxWidth().liquidGlass(experimental, shape, MaterialTheme.colorScheme.surface, dark, 3)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(day.icon, fontSize = 27.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(day.day, fontWeight = FontWeight.SemiBold)
                Text(day.description.replaceFirstChar { it.titlecase(Locale.getDefault()) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${day.min}°", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text("${day.max}°", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WeatherErrorCard(message: String, onRetry: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🌡️", fontSize = 42.sp)
            Text(message, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text("Нажмите, чтобы повторить", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onRetry).padding(8.dp))
        }
    }
}

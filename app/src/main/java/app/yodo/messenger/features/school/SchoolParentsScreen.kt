package app.yodo.messenger.features.school

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * НОВОЕ (раздел «Школа»): контакты гимназии для родителей — перенос раздела
 * «Для родителей» из бота (адрес, телефон, почта, сайт, VK).
 */
@Composable
fun SchoolParentsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Scaffold(
        topBar = { SchoolTopBar("Родителям", onBackClick) },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Text(
                    SchoolData.SCHOOL_NAME,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Образовательное учреждение, предлагающее широкий спектр учебных программ.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SchoolRowCard(
                icon = Icons.Filled.Home,
                title = SchoolData.SCHOOL_ADDRESS,
                subtitle = "Адрес гимназии"
            )
            SchoolRowCard(
                icon = Icons.Filled.Call,
                title = SchoolData.SCHOOL_PHONE,
                subtitle = "Телефон — нажмите, чтобы позвонить",
                onClick = { open(SchoolData.SCHOOL_PHONE_DIAL) }
            )
            SchoolRowCard(
                icon = Icons.Filled.Email,
                title = SchoolData.SCHOOL_EMAIL,
                subtitle = "Электронная почта — нажмите, чтобы написать",
                onClick = { open("mailto:${SchoolData.SCHOOL_EMAIL}") }
            )
            SchoolRowCard(
                icon = Icons.Filled.Language,
                title = "🌐 Сайт гимназии",
                subtitle = SchoolData.SCHOOL_SITE.removePrefix("https://"),
                onClick = { open(SchoolData.SCHOOL_SITE) }
            )
            SchoolRowCard(
                icon = Icons.Filled.Public,
                title = "Группа ВКонтакте",
                subtitle = SchoolData.SCHOOL_VK.removePrefix("https://"),
                onClick = { open(SchoolData.SCHOOL_VK) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

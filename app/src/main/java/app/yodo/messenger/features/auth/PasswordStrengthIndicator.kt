package app.yodo.messenger.features.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun passwordStrengthLabel(level: PasswordStrengthLevel): String = when (level) {
    PasswordStrengthLevel.WEAK -> "Ненадёжный"
    PasswordStrengthLevel.MEDIUM -> "Средний"
    PasswordStrengthLevel.STRONG -> "Надёжный"
}

@Composable
fun passwordStrengthColor(level: PasswordStrengthLevel): Color = when (level) {
    PasswordStrengthLevel.WEAK -> Color(0xFFE53935)
    PasswordStrengthLevel.MEDIUM -> Color(0xFFFFA000)
    PasswordStrengthLevel.STRONG -> Color(0xFF43A047)
}

@Composable
fun PasswordStrengthIndicator(
    result: PasswordStrengthResult,
    modifier: Modifier = Modifier
) {
    val color = passwordStrengthColor(result.level)
    val fraction = when (result.level) {
        PasswordStrengthLevel.WEAK -> 0.33f
        PasswordStrengthLevel.MEDIUM -> 0.66f
        PasswordStrengthLevel.STRONG -> 1f
    }
    val animatedFraction by animateFloatAsState(targetValue = fraction, label = "password_strength")

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Надёжность пароля",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                passwordStrengthLabel(result.level),
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }

        Spacer4()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            ) {}
        }

        Spacer8()

        ChecklistRow("Минимум 8 символов", result.checklist.hasMinLength)
        ChecklistRow("Хотя бы один спецсимвол", result.checklist.hasSpecialChar)
        ChecklistRow("Хотя бы одна цифра", result.checklist.hasDigit)
        ChecklistRow("Заглавные и строчные буквы", result.checklist.hasUpperAndLower)
    }
}

@Composable
private fun ChecklistRow(label: String, passed: Boolean) {
    val color = if (passed) Color(0xFF43A047) else Color(0xFFE53935)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (passed) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = null,
            tint = color,
            modifier = Modifier.height(16.dp)
        )
        Row(modifier = Modifier.padding(start = 6.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Spacer4() = androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))

@Composable
private fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))

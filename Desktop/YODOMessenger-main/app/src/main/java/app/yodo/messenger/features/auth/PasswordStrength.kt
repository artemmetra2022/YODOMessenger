package app.yodo.messenger.features.auth

import kotlin.random.Random

enum class PasswordStrengthLevel {
    WEAK, MEDIUM, STRONG
}

data class PasswordChecklist(
    val hasMinLength: Boolean,
    val hasSpecialChar: Boolean,
    val hasDigit: Boolean,
    val hasUpperAndLower: Boolean
)

data class PasswordStrengthResult(
    val level: PasswordStrengthLevel,
    val checklist: PasswordChecklist
)

private val SPECIAL_CHARS_REGEX = Regex("[^A-Za-z0-9]")

fun evaluatePasswordStrength(password: String): PasswordStrengthResult {
    val checklist = PasswordChecklist(
        hasMinLength = password.length >= 8,
        hasSpecialChar = SPECIAL_CHARS_REGEX.containsMatchIn(password),
        hasDigit = password.any { it.isDigit() },
        hasUpperAndLower = password.any { it.isUpperCase() } && password.any { it.isLowerCase() }
    )

    val score = listOf(
        checklist.hasMinLength,
        checklist.hasSpecialChar,
        checklist.hasDigit,
        checklist.hasUpperAndLower
    ).count { it }

    val level = when {
        password.isEmpty() -> PasswordStrengthLevel.WEAK
        score <= 1 -> PasswordStrengthLevel.WEAK
        score in 2..3 -> PasswordStrengthLevel.MEDIUM
        else -> PasswordStrengthLevel.STRONG
    }

    // Надёжным пароль может считаться только если выполнены оба обязательных условия
    val finalLevel = if (level == PasswordStrengthLevel.STRONG &&
        (!checklist.hasMinLength || !checklist.hasSpecialChar)
    ) {
        PasswordStrengthLevel.MEDIUM
    } else {
        level
    }

    return PasswordStrengthResult(finalLevel, checklist)
}

fun generateStrongPassword(length: Int = 14): String {
    val lower = "abcdefghijkmnopqrstuvwxyz"
    val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    val digits = "23456789"
    val special = "!@#$%^&*()-_=+?"
    val all = lower + upper + digits + special

    val required = listOf(
        lower[Random.nextInt(lower.length)],
        upper[Random.nextInt(upper.length)],
        digits[Random.nextInt(digits.length)],
        special[Random.nextInt(special.length)]
    )

    val rest = (length - required.size).coerceAtLeast(0)
    val body = required + (0 until rest).map { all[Random.nextInt(all.length)] }

    return body.shuffled().joinToString("")
}

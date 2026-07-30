package app.yodo.messenger.core.util

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException

/**
 * НОВОЕ (п.30): единая точка перевода ошибок Firebase (Auth/Firestore/Storage)
 * в понятные пользователю сообщения на русском языке.
 *
 * Раньше в разных репозиториях просто показывался e.message, который у Firebase
 * приходит на английском ("The password is invalid...", "There is no user record...")
 * — пользователь видел смесь русского интерфейса с английскими системными ошибками.
 *
 * Используем везде вместо прямого обращения к e.message:
 *   catch (e: Exception) { SendMessageResult.Error(e.toUserMessage("Не удалось отправить сообщение")) }
 *
 * fallback — сообщение по умолчанию для конкретного действия, если тип ошибки не распознан.
 */
fun Throwable.toUserMessage(fallback: String): String {
    // Сначала — типизированные исключения FirebaseAuth, они надёжнее сравнения строк.
    when (this) {
        is FirebaseAuthInvalidCredentialsException -> return "Неверный пароль"
        is FirebaseAuthInvalidUserException -> return "Аккаунт не найден"
        is FirebaseAuthUserCollisionException -> return "Этот email уже зарегистрирован"
        is FirebaseAuthWeakPasswordException -> return "Пароль слишком простой. Используйте минимум 6 символов"
        is FirebaseTooManyRequestsException -> return "Слишком много попыток. Попробуйте позже"
        is FirebaseNetworkException -> return "Нет подключения к интернету"
    }

    if (this is FirebaseFirestoreException) {
        return when (code) {
            FirebaseFirestoreException.Code.UNAVAILABLE -> "Нет подключения к интернету"
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Недостаточно прав для этого действия"
            FirebaseFirestoreException.Code.NOT_FOUND -> "Данные не найдены"
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> "Превышено время ожидания. Попробуйте ещё раз"
            else -> fallback
        }
    }

    if (this is StorageException) {
        return when (errorCode) {
            StorageException.ERROR_QUOTA_EXCEEDED -> "Превышен лимит хранилища"
            StorageException.ERROR_NOT_AUTHENTICATED -> "Вы не авторизованы"
            StorageException.ERROR_NOT_AUTHORIZED -> "Недостаточно прав для этого действия"
            else -> "Не удалось загрузить файл"
        }
    }

    // Фолбэк — распознаём типовые формулировки Firebase по тексту сообщения
    // (актуально, если по какой-то причине пришло не типизированное исключение).
    val raw = message ?: return fallback
    return when {
        raw.contains("badly formatted", ignoreCase = true) -> "Некорректный email"
        raw.contains("password is invalid", ignoreCase = true) -> "Неверный пароль"
        raw.contains("no user record", ignoreCase = true) -> "Аккаунт не найден"
        raw.contains("USERNAME_TAKEN", ignoreCase = true) -> "Этот username уже занят"
        raw.contains("email address is already in use", ignoreCase = true) -> "Этот email уже зарегистрирован"
        raw.contains("network", ignoreCase = true) -> "Проблема с подключением к сети"
        raw.contains("too many requests", ignoreCase = true) -> "Слишком много попыток. Попробуйте позже"
        else -> fallback
    }
}

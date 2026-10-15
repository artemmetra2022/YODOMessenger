package app.yodo.messenger.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * НОВОЕ (motion-система): единые кривые и длительности анимаций для всего приложения.
 *
 * До этого файла каждый экран задавал tween()/spring() с собственными числами
 * "на глаз" — где-то 150мс, где-то 300мс, где-то стандартный FastOutSlowIn.
 * Из-за этого разные части приложения двигались с разным "характером":
 * список чатов дёргался быстрее, чем открывался чат, свайпы отменялись
 * резче, чем появлялись. Один набор токенов ниже — единственный источник
 * правды для длительности и кривой; конкретные places (список чатов,
 * переходы экранов, жесты) просто ссылаются на них.
 *
 * Принцип подбора чисел:
 *  - INSTANT/FAST — обратная связь на прямое касание (нажатие, свайп) должна
 *    ощущаться мгновенной, иначе палец "обгоняет" интерфейс.
 *  - MEDIUM — появление/исчезновение элементов списка, разворачивание панелей.
 *  - SLOW — крупные переходы между экранами, где глаз должен успеть
 *    проследить перемещение.
 *  - EmphasizedEasing — стандартная кривая Material 3 для входа контента
 *    (быстрый старт, мягкое торможение) — используется почти everywhere.
 *  - StandardEasing — для симметричных изменений состояния (например,
 *    изменение цвета фона при свайпе), где нет выраженного "прилёта".
 */
object YodoMotion {

    // Длительности, мс
    const val DURATION_INSTANT = 90
    const val DURATION_FAST = 150
    const val DURATION_MEDIUM = 260
    const val DURATION_SLOW = 380

    // Material 3 "emphasized" — резкий старт, плавное затухание.
    // Используется для входа новых элементов (новая строка чата, шторка).
    val EmphasizedEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    // Симметричная кривая для смены состояния без "прилёта" (цвет, alpha).
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    // Кривая для элементов, покидающих экран (свайп-удаление, закрытие).
    val ExitEasing: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    fun <T> emphasized(durationMillis: Int = DURATION_MEDIUM): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = EmphasizedEasing)

    fun <T> standard(durationMillis: Int = DURATION_FAST): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = StandardEasing)

    fun <T> exit(durationMillis: Int = DURATION_FAST): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = ExitEasing)

    // Пружина для перетаскиваемых элементов (свайп чата) — палец должен
    // ощущаться "прилипшим" во время драга и естественно долетать при отпускании.
    fun <T> drag(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = 700f)

    // Более отчётливая пружина для подтверждающих действий (закрепление,
    // прочитано) — лёгкий "bounce", подчёркивающий завершение действия.
    fun <T> confirm(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.6f, stiffness = 380f)
}

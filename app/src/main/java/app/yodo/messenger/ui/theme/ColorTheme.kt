package app.yodo.messenger.ui.theme

import androidx.compose.ui.graphics.Color

object TelegramColors {
    val lightOutgoing = Color(0xFFEEFFDE)
    val lightIncoming = Color(0xFFFFFFFF)
    val lightOutgoingTime = Color(0xFF66847C)
    val lightIncomingTime = Color(0xFF8A8A8A)
    val lightOutgoingLink = Color(0xFF2F8243)
    val lightIncomingLink = Color(0xFF2478C7)
    val lightBackground = Color(0xFFFFFFFF)
    val darkOutgoing = Color(0xFF2B5278)
    val darkIncoming = Color(0xFF182533)
    val darkOutgoingTime = Color(0xFF82A6C8)
    val darkIncomingTime = Color(0xFF6D7D8E)
    val darkBackground = Color(0xFF0E1621)
}

enum class ColorThemeName(val displayName: String) {
    BLUE("Синяя"), GREEN("Зелёная"), RED("Красная"), PURPLE("Фиолетовая"),
    ORANGE("Оранжевая"), PINK("Розовая"), TEAL("Бирюзовая"), BEIGE("Бежевая"),
    // п.31: 10 новых тем
    INDIGO("Индиго"), LIME("Лаймовая"), CYAN("Голубая"), AMBER("Янтарная"),
    EMERALD("Изумрудная"), VIOLET("Фиалковая"), CORAL("Коралловая"),
    SLATE("Серая"), GOLD("Золотая"), MINT("Мятная")
}

data class ColorTheme(
    val name: ColorThemeName,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val bubbleOwn: Color,
    val bubbleOwnText: Color,
    val bubbleOther: Color,
    val bubbleOtherText: Color,
    val backgroundDark: Color,
    val surfaceDark: Color,
    val backgroundLight: Color,
    val surfaceLight: Color,
    val onSurfaceDark: Color,
    val onSurfaceLight: Color,
    val online: Color = Color(0xFF22C55E),
    val error: Color = Color(0xFFEF4444)
)

val BlueTheme = ColorTheme(
    name = ColorThemeName.BLUE,
    primary = Color(0xFF3B82F6), secondary = Color(0xFF2563EB), accent = Color(0xFF06B6D4),
    bubbleOwn = Color(0xFF3B82F6), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF1E293B), bubbleOtherText = Color(0xFFE2E8F0),
    backgroundDark = Color(0xFF0F172A), surfaceDark = Color(0xFF1E293B),
    backgroundLight = Color(0xFFF8FAFC), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFE2E8F0), onSurfaceLight = Color(0xFF0F172A)
)
val GreenTheme = ColorTheme(
    name = ColorThemeName.GREEN,
    primary = Color(0xFF22C55E), secondary = Color(0xFF16A34A), accent = Color(0xFF4ADE80),
    bubbleOwn = Color(0xFF22C55E), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF1A2E1A), bubbleOtherText = Color(0xFFD1FAE5),
    backgroundDark = Color(0xFF0A1A0A), surfaceDark = Color(0xFF1A2E1A),
    backgroundLight = Color(0xFFF0FDF4), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFD1FAE5), onSurfaceLight = Color(0xFF0A1A0A)
)
val RedTheme = ColorTheme(
    name = ColorThemeName.RED,
    primary = Color(0xFFEF4444), secondary = Color(0xFFDC2626), accent = Color(0xFFF87171),
    bubbleOwn = Color(0xFFEF4444), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF2E1A1A), bubbleOtherText = Color(0xFFFEE2E2),
    backgroundDark = Color(0xFF1A0A0A), surfaceDark = Color(0xFF2E1A1A),
    backgroundLight = Color(0xFFFEF2F2), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFFEE2E2), onSurfaceLight = Color(0xFF1A0A0A)
)
val PurpleTheme = ColorTheme(
    name = ColorThemeName.PURPLE,
    primary = Color(0xFF8B5CF6), secondary = Color(0xFF7C3AED), accent = Color(0xFFA78BFA),
    bubbleOwn = Color(0xFF8B5CF6), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF231A3E), bubbleOtherText = Color(0xFFEDE9FE),
    backgroundDark = Color(0xFF130A2A), surfaceDark = Color(0xFF231A3E),
    backgroundLight = Color(0xFFF5F3FF), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFEDE9FE), onSurfaceLight = Color(0xFF130A2A)
)
val OrangeTheme = ColorTheme(
    name = ColorThemeName.ORANGE,
    primary = Color(0xFFF97316), secondary = Color(0xFFEA580C), accent = Color(0xFFFB923C),
    bubbleOwn = Color(0xFFF97316), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF2E2010), bubbleOtherText = Color(0xFFFFEDD5),
    backgroundDark = Color(0xFF1A1005), surfaceDark = Color(0xFF2E2010),
    backgroundLight = Color(0xFFFFF7ED), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFFFEDD5), onSurfaceLight = Color(0xFF1A1005)
)
val PinkTheme = ColorTheme(
    name = ColorThemeName.PINK,
    primary = Color(0xFFEC4899), secondary = Color(0xFFDB2777), accent = Color(0xFFF472B6),
    bubbleOwn = Color(0xFFEC4899), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF2E1A24), bubbleOtherText = Color(0xFFFCE7F3),
    backgroundDark = Color(0xFF1A0A12), surfaceDark = Color(0xFF2E1A24),
    backgroundLight = Color(0xFFFDF2F8), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFFCE7F3), onSurfaceLight = Color(0xFF1A0A12)
)
val TealTheme = ColorTheme(
    name = ColorThemeName.TEAL,
    primary = Color(0xFF14B8A6), secondary = Color(0xFF0D9488), accent = Color(0xFF2DD4BF),
    bubbleOwn = Color(0xFF14B8A6), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF102E2A), bubbleOtherText = Color(0xFFCCFBF1),
    backgroundDark = Color(0xFF051A17), surfaceDark = Color(0xFF102E2A),
    backgroundLight = Color(0xFFF0FDFA), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFCCFBF1), onSurfaceLight = Color(0xFF051A17)
)
val BeigeTheme = ColorTheme(
    name = ColorThemeName.BEIGE,
    primary = Color(0xFFD97706), secondary = Color(0xFFB45309), accent = Color(0xFFFBBF24),
    bubbleOwn = Color(0xFFD97706), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFFF5F0E8), bubbleOtherText = Color(0xFF44403C),
    backgroundDark = Color(0xFF1C1917), surfaceDark = Color(0xFF292524),
    backgroundLight = Color(0xFFFAF8F5), surfaceLight = Color(0xFFFFFBF5),
    onSurfaceDark = Color(0xFFE7E5E4), onSurfaceLight = Color(0xFF292524)
)

// ═══════════════ п.31: 10 НОВЫХ ТЕМ ═══════════════

val IndigoTheme = ColorTheme(
    name = ColorThemeName.INDIGO,
    primary = Color(0xFF4F46E5), secondary = Color(0xFF4338CA), accent = Color(0xFF818CF8),
    bubbleOwn = Color(0xFF4F46E5), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF1E1B4B), bubbleOtherText = Color(0xFFE0E7FF),
    backgroundDark = Color(0xFF0F0E2A), surfaceDark = Color(0xFF1E1B4B),
    backgroundLight = Color(0xFFEEF2FF), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFE0E7FF), onSurfaceLight = Color(0xFF1E1B4B)
)
val LimeTheme = ColorTheme(
    name = ColorThemeName.LIME,
    primary = Color(0xFF84CC16), secondary = Color(0xFF65A30D), accent = Color(0xFFA3E635),
    bubbleOwn = Color(0xFF65A30D), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF1A2E05), bubbleOtherText = Color(0xFFECFCCB),
    backgroundDark = Color(0xFF0A1500), surfaceDark = Color(0xFF1A2E05),
    backgroundLight = Color(0xFFF7FEE7), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFECFCCB), onSurfaceLight = Color(0xFF1A2E05)
)
val CyanTheme = ColorTheme(
    name = ColorThemeName.CYAN,
    primary = Color(0xFF06B6D4), secondary = Color(0xFF0891B2), accent = Color(0xFF22D3EE),
    bubbleOwn = Color(0xFF0891B2), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF083344), bubbleOtherText = Color(0xFFCFFAFE),
    backgroundDark = Color(0xFF042F3B), surfaceDark = Color(0xFF083344),
    backgroundLight = Color(0xFFECFEFF), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFCFFAFE), onSurfaceLight = Color(0xFF083344)
)
val AmberTheme = ColorTheme(
    name = ColorThemeName.AMBER,
    primary = Color(0xFFF59E0B), secondary = Color(0xFFD97706), accent = Color(0xFFFBBF24),
    bubbleOwn = Color(0xFFD97706), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF2E2005), bubbleOtherText = Color(0xFFFEF3C7),
    backgroundDark = Color(0xFF1A1200), surfaceDark = Color(0xFF2E2005),
    backgroundLight = Color(0xFFFFFBEB), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFFEF3C7), onSurfaceLight = Color(0xFF2E2005)
)
val EmeraldTheme = ColorTheme(
    name = ColorThemeName.EMERALD,
    primary = Color(0xFF10B981), secondary = Color(0xFF059669), accent = Color(0xFF34D399),
    bubbleOwn = Color(0xFF059669), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF022C22), bubbleOtherText = Color(0xFFD1FAE5),
    backgroundDark = Color(0xFF011A14), surfaceDark = Color(0xFF022C22),
    backgroundLight = Color(0xFFECFDF5), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFD1FAE5), onSurfaceLight = Color(0xFF022C22)
)
val VioletTheme = ColorTheme(
    name = ColorThemeName.VIOLET,
    primary = Color(0xFFA78BFA), secondary = Color(0xFF8B5CF6), accent = Color(0xFFC4B5FD),
    bubbleOwn = Color(0xFF8B5CF6), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF2E1065), bubbleOtherText = Color(0xFFEDE9FE),
    backgroundDark = Color(0xFF1A0533), surfaceDark = Color(0xFF2E1065),
    backgroundLight = Color(0xFFF5F3FF), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFEDE9FE), onSurfaceLight = Color(0xFF2E1065)
)
val CoralTheme = ColorTheme(
    name = ColorThemeName.CORAL,
    primary = Color(0xFFFB7185), secondary = Color(0xFFF43F5E), accent = Color(0xFFFDA4AF),
    bubbleOwn = Color(0xFFF43F5E), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF3B0A14), bubbleOtherText = Color(0xFFFFE4E6),
    backgroundDark = Color(0xFF1F0509), surfaceDark = Color(0xFF3B0A14),
    backgroundLight = Color(0xFFFFF1F2), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFFFE4E6), onSurfaceLight = Color(0xFF3B0A14)
)
val SlateTheme = ColorTheme(
    name = ColorThemeName.SLATE,
    primary = Color(0xFF64748B), secondary = Color(0xFF475569), accent = Color(0xFF94A3B8),
    bubbleOwn = Color(0xFF475569), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF1E293B), bubbleOtherText = Color(0xFFE2E8F0),
    backgroundDark = Color(0xFF0F172A), surfaceDark = Color(0xFF1E293B),
    backgroundLight = Color(0xFFF8FAFC), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFE2E8F0), onSurfaceLight = Color(0xFF1E293B)
)
val GoldTheme = ColorTheme(
    name = ColorThemeName.GOLD,
    primary = Color(0xFFEAB308), secondary = Color(0xFFCA8A04), accent = Color(0xFFFDE047),
    bubbleOwn = Color(0xFFCA8A04), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF2E2505), bubbleOtherText = Color(0xFFFEF9C3),
    backgroundDark = Color(0xFF1A1500), surfaceDark = Color(0xFF2E2505),
    backgroundLight = Color(0xFFFEFCE8), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFFEF9C3), onSurfaceLight = Color(0xFF2E2505)
)
val MintTheme = ColorTheme(
    name = ColorThemeName.MINT,
    primary = Color(0xFF34D399), secondary = Color(0xFF10B981), accent = Color(0xFF6EE7B7),
    bubbleOwn = Color(0xFF10B981), bubbleOwnText = Color.White,
    bubbleOther = Color(0xFF064E3B), bubbleOtherText = Color(0xFFD1FAE5),
    backgroundDark = Color(0xFF022C22), surfaceDark = Color(0xFF064E3B),
    backgroundLight = Color(0xFFF0FDF4), surfaceLight = Color(0xFFFFFFFF),
    onSurfaceDark = Color(0xFFD1FAE5), onSurfaceLight = Color(0xFF064E3B)
)

val allColorThemes = listOf(
    BlueTheme, GreenTheme, RedTheme, PurpleTheme,
    OrangeTheme, PinkTheme, TealTheme, BeigeTheme,
    IndigoTheme, LimeTheme, CyanTheme, AmberTheme,
    EmeraldTheme, VioletTheme, CoralTheme, SlateTheme,
    GoldTheme, MintTheme
)

fun getColorThemeByName(name: String): ColorTheme {
    return allColorThemes.find { it.name.name == name } ?: BlueTheme
}

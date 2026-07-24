package app.yodo.messenger.ui.theme

import androidx.compose.ui.graphics.Color

enum class ColorThemeName(val displayName: String) {
    BLUE("Синяя"), GREEN("Зелёная"), RED("Красная"), PURPLE("Фиолетовая"),
    ORANGE("Оранжевая"), PINK("Розовая"), TEAL("Бирюзовая"), BEIGE("Бежевая")
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

val allColorThemes = listOf(
    BlueTheme, GreenTheme, RedTheme, PurpleTheme,
    OrangeTheme, PinkTheme, TealTheme, BeigeTheme
)

fun getColorThemeByName(name: String): ColorTheme {
    return allColorThemes.find { it.name.name == name } ?: BlueTheme
}

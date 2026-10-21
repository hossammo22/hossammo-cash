package com.hossammo.cash.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hossammo.cash.R
import com.hossammo.cash.data.settings.ThemeMode

/** ألوان الهوية. */
object Brand {
    val Navy = Color(0xFF172A3A)
    val Gold = Color(0xFFC69B35)
    val Cream = Color(0xFFF8F3E7)
    val Green = Color(0xFF2E8B57)
    val Red = Color(0xFFC94C4C)
    val Blue = Color(0xFF246BCE)
    val NavyLight = Color(0xFF2A4256)
    val GoldLight = Color(0xFFE3C579)
}

@Immutable
data class FinanceColors(
    val increase: Color,
    val decrease: Color,
    val stable: Color,
    val editable: Color,
    val card: Color,
    val gold: Color,
)

val LocalFinanceColors = staticCompositionLocalOf {
    FinanceColors(Brand.Green, Brand.Red, Brand.Gold, Brand.Blue, Color.White, Brand.Gold)
}

private val LightColors: ColorScheme = lightColorScheme(
    primary = Brand.Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E2EC),
    onPrimaryContainer = Brand.Navy,
    secondary = Brand.Gold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E4BE),
    onSecondaryContainer = Color(0xFF3E2E05),
    tertiary = Brand.Blue,
    background = Brand.Cream,
    onBackground = Color(0xFF1B1B1B),
    surface = Color(0xFFFFFCF5),
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFEFE8D8),
    onSurfaceVariant = Color(0xFF4A4639),
    surfaceContainer = Color(0xFFF4EEDF),
    surfaceContainerHigh = Color(0xFFEFE8D8),
    surfaceContainerLow = Color(0xFFFAF6EC),
    error = Brand.Red,
    outline = Color(0xFF8A8474),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Brand.GoldLight,
    onPrimary = Color(0xFF2B2000),
    primaryContainer = Brand.NavyLight,
    onPrimaryContainer = Color(0xFFE6EEF5),
    secondary = Brand.Gold,
    onSecondary = Color(0xFF2B2000),
    secondaryContainer = Color(0xFF4A3B12),
    onSecondaryContainer = Color(0xFFF3E4BE),
    tertiary = Color(0xFF8DB4F0),
    background = Color(0xFF0F1C27),
    onBackground = Color(0xFFE7E3D8),
    surface = Color(0xFF14222E),
    onSurface = Color(0xFFE7E3D8),
    surfaceVariant = Color(0xFF233544),
    onSurfaceVariant = Color(0xFFC3C9CF),
    surfaceContainer = Color(0xFF1A2A37),
    surfaceContainerHigh = Color(0xFF223443),
    surfaceContainerLow = Color(0xFF162530),
    error = Color(0xFFE57373),
    outline = Color(0xFF7E8B96),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun arabicFont(weight: FontWeight) = Font(
    R.font.noto_sans_arabic, weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val AppFontFamily = FontFamily(
    arabicFont(FontWeight.Normal),
    arabicFont(FontWeight.Medium),
    arabicFont(FontWeight.SemiBold),
    arabicFont(FontWeight.Bold),
)

private fun appTypography(): Typography {
    val base = Typography()
    fun TextStyle.f() = copy(fontFamily = AppFontFamily)
    return Typography(
        displayLarge = base.displayLarge.f(), displayMedium = base.displayMedium.f(), displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(), headlineMedium = base.headlineMedium.f().copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.f().copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.f().copy(fontWeight = FontWeight.Bold), titleMedium = base.titleMedium.f().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.f().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.f(), bodyMedium = base.bodyMedium.f(), bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(), labelMedium = base.labelMedium.f(), labelSmall = base.labelSmall.f().copy(fontSize = 11.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun HossamMoCashTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val finance = if (dark) FinanceColors(Color(0xFF5CC58A), Color(0xFFE57373), Brand.GoldLight, Color(0xFF8DB4F0), Color(0xFF1A2A37), Brand.GoldLight)
    else FinanceColors(Brand.Green, Brand.Red, Brand.Gold, Brand.Blue, Color.White, Brand.Gold)
    androidx.compose.runtime.CompositionLocalProvider(LocalFinanceColors provides finance) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = appTypography(),
            shapes = AppShapes,
            content = content,
        )
    }
}

fun parseHexColor(hex: String?, fallback: Color = Brand.Gold): Color = try {
    if (hex == null) fallback else Color(android.graphics.Color.parseColor(hex))
} catch (e: IllegalArgumentException) {
    fallback
}

/** لوحة ألوان مقترحة للعناصر والفئات. */
val ItemPalette = listOf(
    "#246BCE", "#2E8B57", "#C69B35", "#7B4FB5", "#5E8C31", "#8D5A3B", "#1F7A6D", "#0F6E3F",
    "#B5473A", "#3D5A99", "#D17A22", "#008B8B", "#A23B72", "#5C6BC0", "#6D4C41", "#172A3A",
)

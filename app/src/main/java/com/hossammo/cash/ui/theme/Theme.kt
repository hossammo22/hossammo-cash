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

/** ألوان الهوية: كحلي عميق + ذهبي. */
object Brand {
    val Navy = Color(0xFF0F2233)
    val NavyDeep = Color(0xFF081521)
    val Gold = Color(0xFFC9A13B)
    val Cream = Color(0xFFF7F4EC)
    val Green = Color(0xFF1E8E5A)
    val Red = Color(0xFFC94C4C)
    val Blue = Color(0xFF2563C9)
    val NavyLight = Color(0xFF223A50)
    val GoldLight = Color(0xFFE6C676)
    val Ink = Color(0xFF0A0E13)
}

@Immutable
data class FinanceColors(
    val increase: Color,
    val decrease: Color,
    val stable: Color,
    val editable: Color,
    val card: Color,
    val gold: Color,
    /** تدرج البطاقات الرئيسية (Hero). */
    val heroStart: Color = Brand.Navy,
    val heroEnd: Color = Brand.NavyDeep,
    val cardBorder: Color = Brand.Gold.copy(alpha = 0.18f),
)

val LocalFinanceColors = staticCompositionLocalOf {
    FinanceColors(Brand.Green, Brand.Red, Brand.Gold, Brand.Blue, Color.White, Brand.Gold)
}

/** هل المظهر الحالي داكن؟ + مبدّل سريع بين الفاتح والداكن. */
data class ThemeToggle(val dark: Boolean, val toggle: () -> Unit)
val LocalThemeToggle = staticCompositionLocalOf { ThemeToggle(false) {} }

private val LightColors: ColorScheme = lightColorScheme(
    primary = Brand.Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E4EE),
    onPrimaryContainer = Brand.Navy,
    secondary = Color(0xFFB08A2E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4E6C2),
    onSecondaryContainer = Color(0xFF3E2E05),
    tertiary = Brand.Blue,
    background = Brand.Cream,
    onBackground = Color(0xFF111418),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF111418),
    surfaceVariant = Color(0xFFEEE8DA),
    onSurfaceVariant = Color(0xFF45413A),
    surfaceContainer = Color(0xFFF3EEE2),
    surfaceContainerHigh = Color(0xFFEDE6D6),
    surfaceContainerLow = Color(0xFFFAF7F0),
    error = Brand.Red,
    outline = Color(0xFF857F70),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Brand.GoldLight,
    onPrimary = Color(0xFF2B2000),
    primaryContainer = Brand.NavyLight,
    onPrimaryContainer = Color(0xFFE6EEF5),
    secondary = Brand.Gold,
    onSecondary = Color(0xFF231A00),
    secondaryContainer = Color(0xFF4A3B12),
    onSecondaryContainer = Color(0xFFF3E4BE),
    tertiary = Color(0xFF8DB4F0),
    background = Brand.Ink,
    onBackground = Color(0xFFEDE9DF),
    surface = Color(0xFF11171E),
    onSurface = Color(0xFFEDE9DF),
    surfaceVariant = Color(0xFF1E2833),
    onSurfaceVariant = Color(0xFFC0C7CE),
    surfaceContainer = Color(0xFF151C24),
    surfaceContainerHigh = Color(0xFF1C2530),
    surfaceContainerLow = Color(0xFF10161C),
    error = Color(0xFFEF8080),
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
    arabicFont(FontWeight.ExtraBold),
)

/** خطوط عريضة وواضحة: النصوص العادية Medium/SemiBold والعناوين Bold/ExtraBold. */
private fun appTypography(): Typography {
    val base = Typography()
    fun TextStyle.f(w: FontWeight) = copy(fontFamily = AppFontFamily, fontWeight = w)
    return Typography(
        displayLarge = base.displayLarge.f(FontWeight.ExtraBold), displayMedium = base.displayMedium.f(FontWeight.ExtraBold), displaySmall = base.displaySmall.f(FontWeight.ExtraBold),
        headlineLarge = base.headlineLarge.f(FontWeight.ExtraBold), headlineMedium = base.headlineMedium.f(FontWeight.ExtraBold),
        headlineSmall = base.headlineSmall.f(FontWeight.Bold),
        titleLarge = base.titleLarge.f(FontWeight.Bold), titleMedium = base.titleMedium.f(FontWeight.Bold),
        titleSmall = base.titleSmall.f(FontWeight.Bold),
        bodyLarge = base.bodyLarge.f(FontWeight.SemiBold), bodyMedium = base.bodyMedium.f(FontWeight.SemiBold), bodySmall = base.bodySmall.f(FontWeight.Medium),
        labelLarge = base.labelLarge.f(FontWeight.Bold), labelMedium = base.labelMedium.f(FontWeight.SemiBold), labelSmall = base.labelSmall.f(FontWeight.SemiBold).copy(fontSize = 11.sp),
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
fun HossamMoCashTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, onToggleTheme: (Boolean) -> Unit = {}, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val finance = if (dark) FinanceColors(
        Color(0xFF5CC58A), Color(0xFFEF8080), Brand.GoldLight, Color(0xFF8DB4F0), Color(0xFF151C24), Brand.GoldLight,
        heroStart = Color(0xFF1B2C3D), heroEnd = Color(0xFF0B1118), cardBorder = Brand.Gold.copy(alpha = 0.22f),
    ) else FinanceColors(Brand.Green, Brand.Red, Color(0xFFB08A2E), Brand.Blue, Color.White, Brand.Gold)
    // لون أيقونات شريط الحالة يتبع مظهر التطبيق (وليس مظهر الهاتف)
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            var c: android.content.Context? = view.context
            while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
            (c as? android.app.Activity)?.window?.let { w ->
                val ctl = androidx.core.view.WindowCompat.getInsetsController(w, view)
                ctl.isAppearanceLightStatusBars = !dark
                ctl.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalFinanceColors provides finance, LocalThemeToggle provides ThemeToggle(dark) { onToggleTheme(!dark) }) {
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

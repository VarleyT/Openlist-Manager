package com.bytegrep.oplist_manager.core.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bytegrep.oplist_manager.core.datastore.ThemeMode

/** 语义颜色 token，来源 DESIGN.md 2.1 */
@Immutable
data class OlColors(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val accent: Color,
    val accentSoft: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val fill: Color,
    val hairline: Color,
    val edge: Color,
    val folder: Color,
    val folderSoft: Color,
)

private val LightOlColors = OlColors(
    bg = Color(0xFFF7F7F8),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF1C1C1E),
    ink2 = Color(0xFF5F6068),
    ink3 = Color(0xFF9A9BA3),
    accent = Color(0xFF0A7CFF),
    accentSoft = Color(0x1A0A7CFF),
    success = Color(0xFF1F9D55),
    successSoft = Color(0x1A1F9D55),
    warning = Color(0xFFB86E00),
    warningSoft = Color(0x1AB86E00),
    danger = Color(0xFFE5483D),
    dangerSoft = Color(0x14E5483D),
    fill = Color(0x0A000000),
    hairline = Color(0x12000000),
    edge = Color(0x0D000000),
    folder = Color(0xFFF5A623),
    folderSoft = Color(0x1FF5A623),
)

private val DarkOlColors = OlColors(
    bg = Color(0xFF0E0E10),
    surface = Color(0xFF1A1A1D),
    ink = Color(0xFFF2F2F4),
    ink2 = Color(0xFFA4A5AD),
    ink3 = Color(0xFF6C6D75),
    accent = Color(0xFF3D97FF),
    accentSoft = Color(0x293D97FF),
    success = Color(0xFF3CCF7A),
    successSoft = Color(0x293CCF7A),
    warning = Color(0xFFFFB340),
    warningSoft = Color(0x29FFB340),
    danger = Color(0xFFFF6961),
    dangerSoft = Color(0x29FF6961),
    fill = Color(0x0FFFFFFF),
    hairline = Color(0x14FFFFFF),
    edge = Color(0x0FFFFFFF),
    folder = Color(0xFFFFC04D),
    folderSoft = Color(0x29FFC04D),
)

val LocalOlColors = staticCompositionLocalOf { LightOlColors }

/** 间距与尺寸 token，来源 DESIGN.md 2.3 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val page = 16.dp
    val rowMinHeight = 52.dp
    val iconBlock = 40.dp
}

object Radius {
    val card = 16.dp
    val button = 14.dp
    val iconBlock = 10.dp
    val pill = 999.dp
}

private val AppTypography = Typography(
    // Large Title 28/35 700
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    // Title 17/22 600
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    // Body 15.5/22
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    // Caption 13/18
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    // Hint 12/17
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(Radius.button),
    large = RoundedCornerShape(Radius.card),
    extraLarge = RoundedCornerShape(24.dp),
)

private fun OlColors.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = accentSoft,
        onPrimaryContainer = accent,
        secondary = accent,
        secondaryContainer = accentSoft,
        onSecondaryContainer = accent,
        background = bg,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = if (dark) Color(0xFF26262A) else Color(0xFFF0F0F2),
        onSurfaceVariant = ink2,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = if (dark) Color(0xFF26262A) else Color(0xFFF0F0F2),
        outline = ink3,
        outlineVariant = hairline,
        error = danger,
        onError = Color.White,
    )
}

/** 动态配色时由系统色派生语义 token */
private fun ColorScheme.toOlColors(dark: Boolean): OlColors {
    val base = if (dark) DarkOlColors else LightOlColors
    return base.copy(
        bg = surfaceContainerLow,
        surface = surfaceContainerLowest,
        ink = onSurface,
        ink2 = onSurfaceVariant,
        accent = primary,
        accentSoft = primary.copy(alpha = if (dark) 0.16f else 0.10f),
    )
}

@Composable
fun OpenListTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val (scheme, olColors) = if (useDynamic) {
        val context = LocalContext.current
        val dyn = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        val ol = dyn.toOlColors(dark)
        dyn.copy(background = ol.bg, surface = ol.surface) to ol
    } else {
        val ol = if (dark) DarkOlColors else LightOlColors
        ol.toColorScheme(dark) to ol
    }
    SyncSystemBarAppearance(dark)
    CompositionLocalProvider(LocalOlColors provides olColors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/** 应用内主题与系统深浅色不一致时，同步状态栏/导航栏图标颜色 */
@Composable
private fun SyncSystemBarAppearance(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

/** 便捷访问：`OpenListTheme.colors.ink2` */
object OpenListTheme {
    val colors: OlColors
        @Composable get() = LocalOlColors.current
}

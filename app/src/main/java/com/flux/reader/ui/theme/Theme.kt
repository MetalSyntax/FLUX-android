package com.flux.reader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.flux.reader.domain.model.AppThemeMode

/** Tokens de FLUX/themes/{dark,black,white}.json (--ui-*, --glass-*, --bg-gradient). */
data class FluxTokens(
    val gradient: Brush,
    val bg: Color, val bgMuted: Color, val bgAccented: Color,
    val text: Color, val textMuted: Color,
    val border: Color, val glass: Color, val glassDark: Color,
    val dark: Boolean
)

private fun tokens(mode: AppThemeMode) = when (mode) {
    AppThemeMode.DARK -> FluxTokens(
        // más claro que la web (#001E26→#00100B, ui-bg #0a0a0a): en el teléfono el casi-negro se perdía; texto atenuado ≥7:1
        Brush.verticalGradient(listOf(Color(0xFF0B2E36), Color(0xFF0C1C17))),
        Color(0xFF151C1B), Color(0xFF1E2725), Color(0xFF2A3532), Color.White, Color(0xFFC4CCCA),
        Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.07f), Color.Black.copy(alpha = 0.35f), true
    )
    AppThemeMode.AMOLED_BLACK -> FluxTokens(
        Brush.verticalGradient(listOf(Color.Black, Color.Black)),
        Color.Black, Color(0xFF0A0A0A), Color(0xFF141414), Color.White, Color(0xFF737373),
        Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.02f), Color.Black.copy(alpha = 0.7f), true
    )
    AppThemeMode.LIGHT -> FluxTokens(
        Brush.verticalGradient(listOf(Color(0xFFF5F5F5), Color(0xFFE4E4E4))),
        Color.White, Color(0xFFF5F5F5), Color(0xFFE5E5E5), Color(0xFF0C0C0C), Color(0xFF4B5563),
        Color.Black.copy(alpha = 0.10f), Color(0xFF0C0C0C).copy(alpha = 0.05f), Color(0xFFF5F5F5).copy(alpha = 0.85f), false
    )
}

val LocalFluxTokens = staticCompositionLocalOf { tokens(AppThemeMode.DARK) }

/** Acentos seleccionables (Tailwind 500/600, como Parity); el primero es el --color-primary de la web. */
val FluxAccents: List<Long> = listOf(
    0xFF00C08B, 0xFF4F46E5, 0xFF7C3AED, 0xFF9333EA, 0xFFC026D3, 0xFFDB2777, 0xFFE11D48, 0xFFDC2626, 0xFFEA580C,
    0xFFD97706, 0xFFCA8A04, 0xFF65A30D, 0xFF16A34A, 0xFF059669, 0xFF0D9488, 0xFF0891B2, 0xFF0284C7, 0xFF2563EB
)

// rounded-xl / 2xl / 3xl / 2rem de la web
private val FluxShapes = Shapes(
    small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun FluxTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    accent: Long = FluxAccents[0],
    content: @Composable () -> Unit
) {
    val t = tokens(themeMode)
    val primary = Color(accent)
    val base = if (t.dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = primary, onPrimary = Color.White,
        primaryContainer = primary.copy(alpha = 0.2f), onPrimaryContainer = t.text,
        secondary = primary, onSecondary = Color.White,
        secondaryContainer = primary.copy(alpha = 0.2f), onSecondaryContainer = t.text,
        // fondo transparente: deja ver el degradado de FluxBackground (body de la web)
        background = Color.Transparent, onBackground = t.text,
        surface = t.bg, onSurface = t.text, surfaceVariant = t.bgAccented, onSurfaceVariant = t.textMuted,
        surfaceContainerLowest = t.bg, surfaceContainerLow = t.bgMuted, surfaceContainer = t.bgMuted,
        surfaceContainerHigh = t.bgAccented, surfaceContainerHighest = t.bgAccented,
        outline = t.textMuted.copy(alpha = 0.6f), outlineVariant = t.border,
        inverseSurface = t.text, inverseOnSurface = t.bg
    )
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val w = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(w, view).apply {
            isAppearanceLightStatusBars = !t.dark
            isAppearanceLightNavigationBars = !t.dark
        }
    }
    CompositionLocalProvider(LocalFluxTokens provides t) {
        MaterialTheme(colorScheme = scheme, typography = FluxTypography, shapes = FluxShapes, content = content)
    }
}

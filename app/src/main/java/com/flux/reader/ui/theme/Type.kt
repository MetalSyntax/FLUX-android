package com.flux.reader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.flux.reader.R

// La web usa Inter 400–900 (index.html); misma fuente variable que Parity.
@OptIn(ExperimentalTextApi::class)
private fun inter(w: Int) = Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Inter = FontFamily(listOf(400, 500, 600, 700, 800, 900).map(::inter))

private fun s(w: FontWeight, size: Int, lh: Int, ls: Double = 0.0) =
    TextStyle(fontFamily = Inter, fontWeight = w, fontSize = size.sp, lineHeight = lh.sp, letterSpacing = ls.sp)

val FluxTypography = Typography(
    displaySmall = s(FontWeight.ExtraBold, 32, 38, -0.5),
    headlineMedium = s(FontWeight.Bold, 24, 30, -0.3),   // text-2xl font-bold tracking-tight
    titleLarge = s(FontWeight.Bold, 20, 26, -0.2),       // text-xl font-bold
    titleMedium = s(FontWeight.Bold, 14, 20),            // text-sm font-bold (títulos de tarjeta)
    titleSmall = s(FontWeight.Bold, 12, 16),
    bodyLarge = s(FontWeight.Normal, 16, 24),
    bodyMedium = s(FontWeight.Normal, 14, 20),
    bodySmall = s(FontWeight.Normal, 12, 16),
    labelLarge = s(FontWeight.Bold, 12, 16),             // text-xs font-bold (botones)
    labelMedium = s(FontWeight.Bold, 10, 14, 0.5),       // text-[10px] font-bold
    labelSmall = s(FontWeight.Bold, 9, 12, 1.5)          // text-[9px] tracking-widest (nav)
)

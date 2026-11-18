package com.flux.reader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.flux.reader.ui.theme.LocalFluxTokens

/** Fondo de la app: el --bg-gradient del tema de la web. */
@Composable
fun FluxBackground(content: @Composable () -> Unit) =
    Box(Modifier.fillMaxSize().background(LocalFluxTokens.current.gradient)) { content() }

/** `.glass` de la web: fondo translúcido + borde --ui-border, rounded-2xl. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val t = LocalFluxTokens.current
    val body: @Composable () -> Unit = { Column(content = content) }
    val border = BorderStroke(1.dp, t.border)
    if (onClick != null) Surface(onClick, modifier, shape = shape, color = t.glass, contentColor = t.text, border = border, content = body)
    else Surface(modifier, shape = shape, color = t.glass, contentColor = t.text, border = border, content = body)
}

/** Nombre histórico: ahora es la tarjeta glass de la web. */
@Composable
fun TonalCard(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(16.dp), content: @Composable ColumnScope.() -> Unit) =
    GlassCard(modifier, shape, content = content)

/** Chip de la web: rounded-xl, 10px bold, borde; activo = bg-primary text-white. */
@Composable
fun FluxChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val t = LocalFluxTokens.current
    FilterChip(
        selected = selected, onClick = onClick, label = label, modifier = modifier, leadingIcon = leadingIcon,
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent, labelColor = t.text.copy(alpha = 0.6f), iconColor = t.text.copy(alpha = 0.6f),
            selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected, borderColor = t.border, selectedBorderColor = Color.Transparent
        )
    )
}

/** Campo de búsqueda de la web: h-14 rounded-3xl, fondo glass, borde que pasa a primary al enfocar. */
@Composable
fun fluxFieldColors(): TextFieldColors {
    val t = LocalFluxTokens.current
    return OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = t.glass, focusedContainerColor = t.glass,
        unfocusedBorderColor = t.border, focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        unfocusedPlaceholderColor = t.textMuted, focusedPlaceholderColor = t.textMuted,
        unfocusedLeadingIconColor = t.textMuted, focusedLeadingIconColor = t.textMuted
    )
}

/** Segmentos del selector de tema: activo = bg-primary text-white. */
@Composable
fun fluxSegmentColors(): SegmentedButtonColors = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = Color.White,
    inactiveContainerColor = Color.Transparent, inactiveContentColor = LocalFluxTokens.current.textMuted,
    activeBorderColor = Color.Transparent, inactiveBorderColor = LocalFluxTokens.current.border
)

/** Selector de acento (rejilla de círculos, como el de Parity). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AccentPicker(selected: Long, accents: List<Long>, onPick: (Long) -> Unit) {
    val t = LocalFluxTokens.current
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
    ) {
        accents.forEach { c ->
            val on = c == selected
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                    .then(if (on) Modifier.border(3.dp, t.text, CircleShape) else Modifier)
                    .clickable { onPick(c) }
            )
        }
    }
}

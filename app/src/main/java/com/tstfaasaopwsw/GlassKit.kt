package com.tstfaasaopwsw

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

/**
 * The app's single source of truth for look and feel: an Apple-style spacing scale, the
 * glass shapes, and a small set of composables built on the Backdrop liquid-glass library.
 * Every surface reuses these — bars, cards, sheets, buttons — so the whole UI reads as one
 * material. Screens never hand-roll a glass effect; they call [Modifier.glass] or a
 * component here.
 */

/** 8-pt spacing scale (Apple-like). Use these instead of literal dp paddings. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val xxxl = 40.dp
}

/** Corner radii by role. Continuous, generous — Apple leans round. */
object GlassShape {
    val pill = RoundedCornerShape(100)
    val bar = RoundedCornerShape(30.dp)
    val card = RoundedCornerShape(24.dp)
    val sheet = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val field = RoundedCornerShape(18.dp)
}

/**
 * The captured layer of the content behind the glass chrome. Provided by [GlassScene]; every
 * glass component reads it, so callers don't thread a backdrop through every level.
 */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Root of a screen: paints the ambient gradient, captures everything drawn in [content] into
 * a backdrop layer, and exposes it via [LocalBackdrop] so [chrome] (bars, floating controls)
 * can blur and refract the content. [chrome] draws outside the captured layer, so it never
 * samples itself.
 */
@Composable
internal fun GlassScene(
    content: @Composable () -> Unit,
    chrome: @Composable BoxScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val backdrop = rememberLayerBackdrop()
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(scheme.surface, scheme.surfaceVariant)),
        ),
    ) {
        val boxScope = this
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            CompositionLocalProvider(LocalBackdrop provides backdrop) { content() }
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) { boxScope.chrome() }
    }
}

/**
 * The app's one tap treatment: a soft spring scale-down, a light haptic tick, and a ripple —
 * so every interactive glass element feels like iOS without each caller re-wiring it.
 */
@Composable
internal fun Modifier.pressable(
    shape: Shape,
    bounded: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 650f),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .clickable(interaction, ripple(bounded = bounded), enabled = enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

@Composable
private fun glassTint(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)

@Composable
private fun glassEdge(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)

/**
 * The one liquid-glass surface used by every component: real blur + refraction lens over
 * [backdrop] (defaults to [LocalBackdrop]), a translucent body tint, and a hairline edge.
 * Falls back to a plain translucent fill when no backdrop is available.
 */
@Composable
internal fun Modifier.glass(
    shape: Shape,
    backdrop: Backdrop? = LocalBackdrop.current,
    tint: Color = glassTint(),
    edge: Color = glassEdge(),
    blurRadius: Dp = 6.dp,
    lensHeight: Dp = 12.dp,
    lensAmount: Dp = 24.dp,
): Modifier {
    if (backdrop == null) return this.background(tint, shape).border(1.dp, edge, shape)
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                blur(blurRadius.toPx())
                lens(lensHeight.toPx(), lensAmount.toPx(), true, false)
            },
            onDrawSurface = { drawRect(tint) },
        )
        .border(1.dp, edge, shape)
}

/**
 * A "frosted" surface: a translucent fill + hairline edge, with NO backdrop RenderEffect.
 * Apple reserves true liquid glass for floating bars; cards and sheets are frosted panels.
 * It's also far cheaper and avoids stacking multiple live glass effects (which can overrun
 * the GPU), so every static container uses this instead of [Modifier.glass].
 */
@Composable
internal fun Modifier.frosted(shape: Shape): Modifier {
    val fill = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f)
    return this.background(fill, shape).border(1.dp, glassEdge(), shape)
}

/** A tappable action row for sheets and lists: leading icon + label, full-width, pressable. */
@Composable
internal fun GlassActionRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    Row(
        Modifier.fillMaxWidth()
            .pressable(GlassShape.card, enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.sm, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Icon(icon, null, tint = color)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

/** A frosted card: the default container for a block of content on a screen. */
@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape.card,
    padding: PaddingValues = PaddingValues(Space.lg),
    content: @Composable () -> Unit,
) {
    Box(modifier.frosted(shape).padding(padding)) { content() }
}

/** A floating full-width glass bar (bottom pill): the browser's address+actions row. */
@Composable
internal fun GlassBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(Space.md, Space.sm, Space.md, Space.lg)
            .glass(GlassShape.pill)
            .padding(horizontal = Space.sm, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        content = content,
    )
}

/** One tab in [GlassSegmentedBar]. */
internal data class GlassTab(
    val icon: ImageVector,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * A small, centered glass segmented control (iOS-style floating tab bar). Wraps its content
 * width so it sits as a compact pill in the middle, not a full-width bar. The selected tab
 * gets a soft capsule highlight. Used for the app's primary navigation.
 */
@Composable
internal fun GlassSegmentedBar(tabs: List<GlassTab>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = Space.lg)
            .glass(GlassShape.pill)
            .padding(Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xxs),
    ) {
        tabs.forEach { t ->
            val color by animateColorAsState(
                if (t.selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "segTint",
            )
            Row(
                Modifier
                    .background(
                        if (t.selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                        GlassShape.pill,
                    )
                    .pressable(GlassShape.pill, onClick = t.onClick)
                    .padding(horizontal = Space.md, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Icon(t.icon, t.label, tint = color, modifier = Modifier.size(18.dp))
                Text(t.label, style = MaterialTheme.typography.labelLarge, color = color)
            }
        }
    }
}

/**
 * A round, evenly-sized tap target (44dp, Apple's minimum) for glass bars. [content] gets
 * the resolved tint (dimmed when disabled) so icons and custom badges share one color.
 */
@Composable
internal fun GlassSlotButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    baseTint: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable (Color) -> Unit,
) {
    val tint by animateColorAsState(
        if (enabled) baseTint else baseTint.copy(alpha = 0.3f), label = "iconTint",
    )
    Box(
        modifier.size(44.dp).pressable(GlassShape.pill, bounded = false, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content(tint) }
}

/** Simple icon tap target for glass bars. */
@Composable
internal fun GlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) = GlassSlotButton(onClick, modifier, enabled, baseTint = tint) { t -> Icon(icon, contentDescription, tint = t) }

/**
 * Content insets so a screen's scroll clears the status bar (top) and the floating segmented
 * nav plus the system nav bar (bottom). Apple keeps the top bar transparent, so no glass
 * chrome up there — just breathing room under the status bar.
 */
val GlassContentInsets: PaddingValues
    @Composable get() {
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        return PaddingValues(top = top + Space.sm, bottom = bottom + 84.dp)
    }

/**
 * A large Apple-style screen title: big, heavy, tight, left-aligned. Sits at the top of a
 * scrolling screen (no glass bar). [trailing] holds optional actions on the right.
 */
@Composable
internal fun GlassLargeTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(Space.xl, Space.md, Space.lg, Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/**
 * A modal bottom sheet in the app's style: a frosted panel attached to the bottom edge (no
 * floating margins, no dead space), rounded only at the top, with a grab handle and roomy
 * padding. One component so every sheet matches and the fiddly ModalBottomSheet wiring
 * (transparent container, zero window insets, our own nav-bar padding) lives in one place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlassBottomSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = Color.Transparent,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Box(Modifier.fillMaxWidth().frosted(GlassShape.sheet)) {
            Column(
                Modifier.padding(Space.xl).windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                Box(
                    Modifier
                        .padding(bottom = Space.lg)
                        .size(width = 36.dp, height = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            GlassShape.pill,
                        ),
                )
                if (title != null) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Box(Modifier.size(Space.lg))
                }
                content()
            }
        }
    }
}

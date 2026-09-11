/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.chords.core.styling

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Desktop layout dimensions shared by Chords components.
 *
 * Applications can replace this value in [ChordsTheme] to change component
 * density without configuring every component independently. Component-level
 * appearance properties, when supplied, take precedence over these defaults.
 * Use `copy` on the current value for a nested override so unrelated settings remain inherited:
 * ```kotlin
 * val dimensions = ChordsTheme.dimensions.copy(
 *     tableRowHeight = 44.dp,
 *     tableRowMaxHeight = 120.dp,
 *     supportingPaneWidth = 480.dp
 * )
 * ```
 *
 * @property spacingXSmall The smallest gap between closely related elements.
 * @property spacingSmall A small gap or inset.
 * @property spacingMedium The ordinary gap between controls.
 * @property spacingLarge The ordinary content inset.
 * @property spacingXLarge The inset between page or dialog regions.
 * @property spacingXXLarge The largest standard section inset.
 * @property appBarHeight The height of the application top bar.
 * @property navigationWidth The width of expanded application navigation.
 * @property navigationItemHeight The height of an application navigation item.
 * @property controlHeight The minimum height of an ordinary input control.
 * @property compactControlHeight The minimum height of a compact control.
 * @property iconButtonSize The default pointer target of an icon button.
 * @property dropdownItemHeight The minimum height of a dropdown item.
 * @property tableHeaderHeight The height of a table header.
 * @property tableRowHeight The height of an ordinary table row.
 * @property tableRowMaxHeight The maximum height of a table row.
 * @property supportingPaneWidth The default width of a supporting details pane.
 */
@Immutable
@Suppress("LongParameterList") // A theme token group is clearer as one immutable value.
public data class ChordsDimensions(
    public val spacingXSmall: Dp = 4.dp,
    public val spacingSmall: Dp = 8.dp,
    public val spacingMedium: Dp = 12.dp,
    public val spacingLarge: Dp = 16.dp,
    public val spacingXLarge: Dp = 24.dp,
    public val spacingXXLarge: Dp = 32.dp,
    public val appBarHeight: Dp = 64.dp,
    public val navigationWidth: Dp = 192.dp,
    public val navigationItemHeight: Dp = 40.dp,
    public val controlHeight: Dp = 44.dp,
    public val compactControlHeight: Dp = 36.dp,
    public val iconButtonSize: Dp = 40.dp,
    public val dropdownItemHeight: Dp = 40.dp,
    public val tableHeaderHeight: Dp = 40.dp,
    public val tableRowHeight: Dp = 40.dp,
    public val tableRowMaxHeight: Dp = 100.dp,
    public val supportingPaneWidth: Dp = 420.dp
)

/**
 * Opacity values used to communicate common interaction states.
 *
 * Supply values in `0F..1F`. Components interpret them for their own surfaces; a value is not
 * necessarily the final pixel opacity. For example, filled actions amplify the hover tint.
 * These values affect appearance, not input handling or whether a control is enabled.
 *
 * Example for a theme's [ChordsTheme] `interaction` argument:
 * ```kotlin
 * val interaction = ChordsTheme.interaction.copy(
 *     hoveredStateAlpha = 0.12F,
 *     disabledContentAlpha = 0.7F
 * )
 * ```
 *
 * @property hoveredStateAlpha The opacity of a hover state layer.
 * @property focusedStateAlpha The opacity of a focused state layer.
 * @property pressedStateAlpha The opacity of a pressed state layer.
 * @property disabledContentAlpha The opacity of disabled content.
 * @property scrimAlpha The opacity of a lightweight modal backdrop.
 */
@Immutable
public data class ChordsInteraction(
    public val hoveredStateAlpha: Float = 0.10f,
    public val focusedStateAlpha: Float = 0.14f,
    public val pressedStateAlpha: Float = 0.18f,
    public val disabledContentAlpha: Float = 0.65f,
    public val scrimAlpha: Float = 0.32f
)

/**
 * Installs the Material theme and Chords desktop defaults for [content].
 *
 * Wrap a Compose window's content, or call this from an `ApplicationTheme` override when using
 * the Chords application shell. Children read colors, typography, and shapes through
 * [MaterialTheme], and desktop dimensions and interaction values through the [ChordsTheme] object.
 *
 * Example with a user-controlled appearance:
 * ```kotlin
 * @Composable
 * fun ThemedContent(dark: Boolean, content: @Composable () -> Unit) {
 *     ChordsTheme(
 *         darkTheme = dark,
 *         dimensions = ChordsDimensions(navigationWidth = 200.dp),
 *         content = content
 *     )
 * }
 * ```
 *
 * [darkTheme] selects the default palette only; an explicit [colorScheme] takes precedence.
 * The default theme also supplies compact typography, shapes, and scrollbar colors. It preserves
 * the current scrollbar dimensions from `LocalScrollbarStyle`.
 *
 * A nested call starts from Chords defaults for omitted arguments. Pass the current values when
 * changing only one setting within an existing theme:
 * ```kotlin
 * @Composable
 * fun SpaciousRows(content: @Composable () -> Unit) {
 *     ChordsTheme(
 *         colorScheme = MaterialTheme.colorScheme,
 *         typography = MaterialTheme.typography,
 *         shapes = MaterialTheme.shapes,
 *         dimensions = ChordsTheme.dimensions.copy(tableRowHeight = 44.dp),
 *         interaction = ChordsTheme.interaction,
 *         content = content
 *     )
 * }
 * ```
 *
 * Prefer theme values for shared appearance, `Application.sharedDefaults` for class-based
 * component defaults, and instance properties for individual exceptions. Plain composable
 * functions use their parameters and the current theme; `sharedDefaults` does not configure them.
 *
 * @param darkTheme Whether the dark color scheme should be used.
 * @param colorScheme The Material color scheme applied to all components.
 * @param typography The Material typography applied to all components.
 * @param shapes The Material shape scale applied to all components.
 * @param dimensions Desktop layout dimensions shared by Chords components.
 * @param interaction Common interaction-state opacity values.
 * @param content The content to which the theme is applied.
 */
@Composable
@Suppress("LongParameterList") // Mirrors MaterialTheme and keeps all theme inputs explicit.
public fun ChordsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorScheme: ColorScheme = if (darkTheme) {
        defaultDarkColorScheme
    } else {
        defaultLightColorScheme
    },
    typography: Typography = defaultTypography,
    shapes: Shapes = defaultShapes,
    dimensions: ChordsDimensions = defaultDimensions,
    interaction: ChordsInteraction = defaultInteraction,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalChordsDimensions provides dimensions,
        LocalChordsInteraction provides interaction,
        LocalContentColor provides colorScheme.onSurface
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes
        ) {
            CompositionLocalProvider(
                LocalScrollbarStyle provides LocalScrollbarStyle.current.copy(
                    unhoverColor = colorScheme.outline,
                    hoverColor = colorScheme.onSurfaceVariant
                ),
                content = content
            )
        }
    }
}

/**
 * Provides the desktop-specific tokens installed by [ChordsTheme].
 *
 * Standard Material values remain available through [MaterialTheme]. Chords
 * adds desktop layout, interaction, and overlay defaults.
 * These properties must be read from composition and reflect the nearest installed theme.
 *
 * Example in a custom information panel:
 * ```kotlin
 * @Composable
 * fun SupportingText(text: String) {
 *     Text(
 *         text = text,
 *         color = ChordsTheme.supportingTextColor,
 *         modifier = Modifier.padding(ChordsTheme.dimensions.spacingLarge)
 *     )
 * }
 * ```
 *
 * Read [dimensions] and [interaction] for layout and feedback. Use [overlayColor] for custom
 * raised surfaces, [disabledContentColor] for disabled foregrounds, and [confirmationTextStyle]
 * for confirmation body text. Call `ChordsTheme(...)` to install these values.
 */
public object ChordsTheme {

    /**
     * The readable foreground shared by disabled controls and their labels.
     */
    public val disabledContentColor: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurface.copy(alpha = interaction.disabledContentAlpha)

    /**
     * The background of menus and dialogs. Dark overlays use a lighter surface
     * because shadows alone do not separate them from the window underneath.
     */
    public val overlayColor: Color
        @Composable
        @ReadOnlyComposable
        get() = with(MaterialTheme.colorScheme) {
            if (surface.luminance() < 0.5f) {
                onSurface.copy(alpha = 0.06f).compositeOver(surfaceVariant)
            } else {
                surface
            }
        }

    /**
     * Supporting text on raised surfaces needs more contrast than text on the page.
     * This tone is independent of onSurfaceVariant so nested dialogs do not compound it.
     */
    internal val overlaySupportingColor: Color
        @Composable
        @ReadOnlyComposable
        get() = with(MaterialTheme.colorScheme) {
            lerp(onSurface, surfaceVariant, OverlaySupportingSurfaceBlend)
        }

    /**
     * Supporting text with a little more contrast for navigation and dense information panels.
     * The foreground stays between secondary labels and primary data in either palette.
     */
    public val supportingTextColor: Color
        @Composable
        @ReadOnlyComposable
        get() = with(MaterialTheme.colorScheme) {
            lerp(onSurfaceVariant, onSurface, 0.4f)
        }

    /**
     * Light body text for confirmation prompts that should not compete with their actions.
     */
    public val confirmationTextStyle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light)

    /**
     * The desktop layout dimensions active in the current composition.
     */
    public val dimensions: ChordsDimensions
        @Composable
        @ReadOnlyComposable
        get() = LocalChordsDimensions.current

    /**
     * The interaction-state opacity values active in the current composition.
     */
    public val interaction: ChordsInteraction
        @Composable
        @ReadOnlyComposable
        get() = LocalChordsInteraction.current
}

/**
 * Creates the Chords light color scheme for [ChordsTheme] or a Material theme.
 *
 * The scheme uses neutral surfaces and reserves blue for selection, focus,
 * links, and primary actions. Applications can pass a different Material
 * [ColorScheme] to [ChordsTheme] to replace all brand colors at once.
 * Override related foreground roles together when changing an accent:
 * ```kotlin
 * val colors = chordsLightColorScheme().copy(
 *     primary = Color(0xFF305C95),
 *     onPrimary = Color.White,
 *     surfaceTint = Color(0xFF305C95)
 * )
 * ```
 *
 * @return A new scheme; creating it does not install a theme.
 */
@Suppress("MagicNumber") // Hex literals make the complete role-based palette auditable.
public fun chordsLightColorScheme(): ColorScheme = lightColorScheme(
    primary = Color(0xFF3E68B2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7EFFF),
    onPrimaryContainer = Color(0xFF153E75),
    inversePrimary = Color(0xFFAFC6FF),
    secondary = Color(0xFF475569),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = Color(0xFF287A5B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD8F3E7),
    onTertiaryContainer = Color(0xFF0B3B2B),
    background = Color(0xFFF3F5F8),
    onBackground = Color(0xFF1F2937),
    surface = Color(0xFFFAFBFC),
    onSurface = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFEEF2F6),
    onSurfaceVariant = Color(0xFF536172),
    surfaceTint = Color(0xFF3E68B2),
    inverseSurface = Color(0xFF2B3038),
    inverseOnSurface = Color(0xFFF4F6F8),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A271A),
    outline = Color(0xFF818E9F),
    outlineVariant = Color(0xFFCFD6DF),
    scrim = Color(0xFF000000)
)

/**
 * Creates the Chords dark color scheme for [ChordsTheme] or a Material theme.
 *
 * Tonal charcoal surfaces distinguish regions without relying on heavy
 * shadows, while a brighter blue keeps selection and keyboard focus visible.
 * Use a separate dark accent and foreground when customizing the palette:
 * ```kotlin
 * val colors = chordsDarkColorScheme().copy(
 *     primary = Color(0xFF91B8EA),
 *     onPrimary = Color(0xFF10243D),
 *     surfaceTint = Color(0xFF91B8EA)
 * )
 * ```
 *
 * @return A new scheme; creating it does not install a theme.
 */
@Suppress("MagicNumber") // Hex literals make the complete role-based palette auditable.
public fun chordsDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = Color(0xFF7CA2D6),
    onPrimary = Color(0xFF07121F),
    primaryContainer = Color(0xFF1C3558),
    onPrimaryContainer = Color(0xFFD7E6FF),
    inversePrimary = Color(0xFF3E68B2),
    secondary = Color(0xFFAAB2BF),
    onSecondary = Color(0xFF26303D),
    secondaryContainer = Color(0xFF343B45),
    onSecondaryContainer = Color(0xFFE0E5ED),
    tertiary = Color(0xFF77C99A),
    onTertiary = Color(0xFF073824),
    tertiaryContainer = Color(0xFF1D4D38),
    onTertiaryContainer = Color(0xFFC2F1D6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFEDF0F5),
    surface = Color(0xFF181B20),
    onSurface = Color(0xFFEDF0F5),
    surfaceVariant = Color(0xFF242932),
    onSurfaceVariant = Color(0xFFADB7C5),
    surfaceTint = Color(0xFF7CA2D6),
    inverseSurface = Color(0xFFE2E6EC),
    inverseOnSurface = Color(0xFF252930),
    error = Color(0xFFEC7C74),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF697586),
    outlineVariant = Color(0xFF343B45),
    scrim = Color(0xFF000000)
)

/**
 * Creates desktop typography that distinguishes headings by size without adding bold weight.
 *
 * All roles use normal weight. Body and large label text default to 14 sp, while smaller roles
 * and headings use their own sizes and line heights. Start from this scale when changing selected
 * roles, then pass the result as [ChordsTheme]'s `typography` argument:
 * ```kotlin
 * val base = chordsTypography()
 * val typography = base.copy(
 *     bodyLarge = base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp)
 * )
 * ```
 *
 * @return A new Material typography scale.
 */
@Suppress("LongMethod") // Keeping the complete type scale together makes it auditable.
public fun chordsTypography(): Typography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 34.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)

/**
 * Creates the restrained corner-radius scale used by Chords.
 *
 * The Material roles run from 4 dp for `extraSmall` to 16 dp for `extraLarge`. Components that
 * choose a fixed shape, such as circular icon buttons, do not use this scale.
 * Pass a modified copy as [ChordsTheme]'s `shapes` argument:
 * ```kotlin
 * val shapes = chordsShapes().copy(small = RoundedCornerShape(8.dp))
 * ```
 *
 * @return A new Material shape scale.
 */
public fun chordsShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

/**
 * The light color scheme reused by the default theme.
 */
private val defaultLightColorScheme: ColorScheme = chordsLightColorScheme()

/**
 * The dark color scheme reused by the default theme.
 */
private val defaultDarkColorScheme: ColorScheme = chordsDarkColorScheme()

/**
 * The typography reused by the default theme.
 */
private val defaultTypography: Typography = chordsTypography()

/**
 * The shape scale reused by the default theme.
 */
private val defaultShapes: Shapes = chordsShapes()

/**
 * The desktop dimensions reused by the default theme and non-composable core defaults.
 */
internal val defaultDimensions: ChordsDimensions = ChordsDimensions()

/**
 * The interaction values reused by the default theme.
 */
private val defaultInteraction: ChordsInteraction = ChordsInteraction()

/**
 * Supplies default desktop dimensions when no Chords theme is installed.
 */
private val LocalChordsDimensions = staticCompositionLocalOf { defaultDimensions }

/**
 * Supplies default interaction values when no Chords theme is installed.
 */
private val LocalChordsInteraction = staticCompositionLocalOf { defaultInteraction }

/**
 * Blends a little of the raised surface into its foreground for secondary labels.
 */
private const val OverlaySupportingSurfaceBlend = 0.14F

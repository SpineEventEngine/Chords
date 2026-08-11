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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
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
    public val appBarHeight: Dp = 52.dp,
    public val navigationWidth: Dp = 224.dp,
    public val navigationItemHeight: Dp = 40.dp,
    public val controlHeight: Dp = 44.dp,
    public val compactControlHeight: Dp = 36.dp,
    public val iconButtonSize: Dp = 40.dp,
    public val dropdownItemHeight: Dp = 40.dp,
    public val tableHeaderHeight: Dp = 40.dp,
    public val tableRowHeight: Dp = 40.dp,
    public val supportingPaneWidth: Dp = 360.dp
)

/**
 * Opacity values used to communicate common interaction states.
 *
 * @property hoveredStateAlpha The opacity of a hover state layer.
 * @property focusedStateAlpha The opacity of a focused state layer.
 * @property pressedStateAlpha The opacity of a pressed state layer.
 * @property disabledContentAlpha The opacity of disabled content.
 * @property scrimAlpha The opacity of a lightweight modal backdrop.
 */
@Immutable
public data class ChordsInteraction(
    public val hoveredStateAlpha: Float = 0.06f,
    public val focusedStateAlpha: Float = 0.08f,
    public val pressedStateAlpha: Float = 0.10f,
    public val disabledContentAlpha: Float = 0.38f,
    public val scrimAlpha: Float = 0.32f
)

/**
 * The default Chords Material 3 theme and its desktop-specific tokens.
 *
 * Standard Material values remain available through [MaterialTheme]. Chords
 * adds only the layout and interaction values that Material does not expose as
 * theme properties for desktop applications.
 */
public object ChordsTheme {

    /**
     * Applies the default Chords look and feel to [content].
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
    public operator fun invoke(
        darkTheme: Boolean = isSystemInDarkTheme(),
        colorScheme: ColorScheme = if (darkTheme) {
            chordsDarkColorScheme()
        } else {
            chordsLightColorScheme()
        },
        typography: Typography = chordsTypography(),
        shapes: Shapes = chordsShapes(),
        dimensions: ChordsDimensions = ChordsDimensions(),
        interaction: ChordsInteraction = ChordsInteraction(),
        content: @Composable () -> Unit
    ) {
        CompositionLocalProvider(
            LocalChordsDimensions provides dimensions,
            LocalChordsInteraction provides interaction
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = typography,
                shapes = shapes,
                content = content
            )
        }
    }

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
 * Creates the modern Chords light color scheme.
 *
 * The scheme uses neutral surfaces and reserves blue for selection, focus,
 * links, and primary actions. Applications can pass a different Material
 * [ColorScheme] to [ChordsTheme] to replace all brand colors at once.
 */
@Suppress("MagicNumber") // Hex literals make the complete role-based palette auditable.
public fun chordsLightColorScheme(): ColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
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
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF1F2937),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFEEF2F6),
    onSurfaceVariant = Color(0xFF5F6B7A),
    surfaceTint = Color(0xFF2563EB),
    inverseSurface = Color(0xFF2B3038),
    inverseOnSurface = Color(0xFFF4F6F8),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A271A),
    outline = Color(0xFFB8C0CC),
    outlineVariant = Color(0xFFDDE2E8),
    scrim = Color(0xFF000000)
)

/**
 * Creates the modern Chords dark color scheme.
 *
 * Tonal charcoal surfaces distinguish regions without relying on heavy
 * shadows, while a brighter blue keeps selection and keyboard focus visible.
 */
@Suppress("MagicNumber") // Hex literals make the complete role-based palette auditable.
public fun chordsDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = Color(0xFF6EA8FE),
    onPrimary = Color(0xFF082E63),
    primaryContainer = Color(0xFF1C3558),
    onPrimaryContainer = Color(0xFFD7E6FF),
    inversePrimary = Color(0xFF2563EB),
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
    onSurfaceVariant = Color(0xFFAAB2BF),
    surfaceTint = Color(0xFF6EA8FE),
    inverseSurface = Color(0xFFE2E6EC),
    inverseOnSurface = Color(0xFF252930),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF46505D),
    outlineVariant = Color(0xFF343B45),
    scrim = Color(0xFF000000)
)

/**
 * Creates the compact desktop typography used by Chords.
 */
@Suppress("LongMethod") // Keeping the complete type scale together makes it auditable.
public fun chordsTypography(): Typography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
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
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)

/**
 * Creates the restrained corner-radius scale used by Chords.
 */
public fun chordsShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

/**
 * Supplies default desktop dimensions when no Chords theme is installed.
 */
private val LocalChordsDimensions = staticCompositionLocalOf { ChordsDimensions() }

/**
 * Supplies default interaction values when no Chords theme is installed.
 */
private val LocalChordsInteraction = staticCompositionLocalOf { ChordsInteraction() }

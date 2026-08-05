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
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.chords.core.layout

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ComposeScene
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerEventType.Companion.Press
import androidx.compose.ui.input.pointer.PointerEventType.Companion.Release
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.awt.Panel
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import java.awt.event.KeyEvent.KEY_PRESSED
import java.awt.event.KeyEvent.KEY_RELEASED
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Surface

/**
 * Displays the given content off-screen, and allows a test to interact with
 * that content the way that a user does.
 *
 * The content is rendered onto an in-memory surface, so a test can be run
 * without a display, and it is measured with a density of one pixel per [Dp]
 * unit, so that the coordinates that a test specifies are the [Dp] values that
 * the content was laid out with.
 *
 * The content is wrapped into a [MaterialTheme], which the components under
 * test require, and is rendered once before this constructor returns, so it is
 * ready to be interacted with. Note that a state change made by a test is
 * applied to the content only upon the subsequent [render] call, just like it
 * is applied only in the next frame of a running application.
 *
 * The scene has to be [closed][close] when a test is done with it, e.g. by
 * using the [use][kotlin.io.use] function.
 *
 * @param width The width available to the content.
 * @param height The height available to the content.
 * @param content The content to be displayed in this scene.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal class TestScene(
    private val width: Dp = DefaultSceneWidth,
    private val height: Dp = DefaultSceneHeight,
    content: @Composable () -> Unit
) : AutoCloseable {

    /**
     * The density that makes a [Dp] unit occupy exactly one pixel.
     */
    private val density = Density(1f)

    /**
     * The scene that composes and lays out the content.
     */
    private val scene = ComposeScene(density = density)

    /**
     * The in-memory surface that the content is rendered onto.
     */
    private val surface = Surface.makeRasterN32Premul(
        width.value.toInt(),
        height.value.toInt()
    )

    /**
     * The number of frames that have been rendered so far.
     */
    private var frames = 0L

    init {
        scene.constraints = with(density) {
            Constraints(maxWidth = width.roundToPx(), maxHeight = height.roundToPx())
        }
        scene.setContent {
            MaterialTheme {
                content()
            }
        }
        render()
    }

    /**
     * The size that the content occupies.
     */
    val contentSize: IntSize
        get() = scene.contentSize

    /**
     * Renders the next frame, which applies all the state changes that have
     * been made since the previous one.
     */
    fun render() {
        frames += 1
        scene.render(surface.canvas, frames * FrameIntervalNanos)
    }

    /**
     * Clicks at the given coordinates within the scene.
     *
     * @param x The horizontal coordinate to click at.
     * @param y The vertical coordinate to click at.
     */
    fun click(x: Dp, y: Dp) {
        click(with(density) { Offset(x.toPx(), y.toPx()) })
    }

    /**
     * Clicks at the given position within the scene.
     *
     * @param position The position to click at, in pixels.
     */
    fun click(position: Offset) {
        scene.sendPointerEvent(Press, position)
        scene.sendPointerEvent(Release, position)
    }

    /**
     * Presses the given key, and delivers the respective event to the content
     * that is currently focused.
     *
     * @param keyCode The code of the key being pressed, as defined by
     *   [java.awt.event.KeyEvent].
     * @param modifiers The mask of the modifier keys that are held while the
     *   key is pressed, as defined by [java.awt.event.KeyEvent].
     */
    fun pressKey(keyCode: Int, modifiers: Int = NoModifierKeys) {
        scene.sendKeyEvent(keyEvent(KEY_PRESSED, keyCode, modifiers))
    }

    /**
     * Releases the given key, and delivers the respective event to the content
     * that is currently focused.
     *
     * @param keyCode The code of the key being released, as defined by
     *   [java.awt.event.KeyEvent].
     * @param modifiers The mask of the modifier keys that are held while the
     *   key is released, as defined by [java.awt.event.KeyEvent].
     */
    fun releaseKey(keyCode: Int, modifiers: Int = NoModifierKeys) {
        scene.sendKeyEvent(keyEvent(KEY_RELEASED, keyCode, modifiers))
    }

    /**
     * Returns the color of the pixel that is displayed at the given
     * coordinates as of the latest [render] call.
     *
     * @param x The horizontal coordinate of the pixel.
     * @param y The vertical coordinate of the pixel.
     * @return The pixel's color, in the ARGB format.
     */
    fun pixelAt(x: Dp, y: Dp): Int {
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(surface.width, surface.height)
        check(surface.readPixels(bitmap, 0, 0)) {
            "Cannot read the pixels rendered by the test scene."
        }
        return bitmap.getColor(x.value.toInt(), y.value.toInt())
    }

    /**
     * Closes the scene along with the surface that it renders onto.
     */
    override fun close() {
        scene.close()
        surface.close()
    }

    /**
     * The defaults that a scene is created with.
     */
    private companion object {

        /**
         * The width available to the scene's content by default.
         */
        val DefaultSceneWidth = 800.dp

        /**
         * The height available to the scene's content by default.
         */
        val DefaultSceneHeight = 800.dp

        /**
         * The time between the frames that the scene renders, which imitates
         * the frame rate of a running application.
         */
        const val FrameIntervalNanos = 16_000_000L

    }
}

/**
 * The mask that states that no modifier key is held.
 */
internal const val NoModifierKeys: Int = 0

/**
 * The time that is reported for the emulated key events.
 *
 * None of the components under test inspects it.
 */
private const val EventTimestamp: Long = 0L

/**
 * Creates a key event of the given type, as if it was triggered by
 * a keyboard of a running application.
 *
 * @param eventType The type of the AWT event to create, which is either
 *   [KEY_PRESSED] or [KEY_RELEASED].
 * @param keyCode The code of the key that triggered the event, as defined by
 *   [java.awt.event.KeyEvent].
 * @param modifiers The mask of the modifier keys held at that moment, as
 *   defined by [java.awt.event.KeyEvent].
 * @return The created event.
 */
internal fun keyEvent(
    eventType: Int,
    keyCode: Int,
    modifiers: Int = NoModifierKeys
): KeyEvent = KeyEvent(
    java.awt.event.KeyEvent(
        Panel(), eventType, EventTimestamp, modifiers, keyCode, CHAR_UNDEFINED
    )
)

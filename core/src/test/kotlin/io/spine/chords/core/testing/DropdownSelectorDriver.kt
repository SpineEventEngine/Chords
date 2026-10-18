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

package io.spine.chords.core.testing

import androidx.compose.material.icons.Icons.Filled
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties.EditableText
import androidx.compose.ui.semantics.SemanticsProperties.Focused
import androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import io.spine.chords.core.layout.TestScene
import io.spine.chords.core.layout.onUiThread
import io.spine.chords.core.styling.ChordsDimensions
import java.awt.event.KeyEvent.VK_DOWN

/**
 * Drives a selector's editor, clear cross, and menu in the shared off-screen scene.
 *
 * @param scene The scene containing exactly one selector.
 * @param dimensions The dimensions supplied to the selector's active theme.
 */
internal class DropdownSelectorDriver(
    private val scene: TestScene,
    private val dimensions: ChordsDimensions = ChordsDimensions()
) {

    /**
     * The editable field, whose bounds anchor its trailing controls.
     */
    private val editor: SemanticsNode
        get() = scene.semanticsNodes()
            .single { it.config.getOrNull(EditableText) != null }

    /**
     * The current label or unfinished search text.
     */
    val input: String
        get() = editor.config[EditableText].text

    /**
     * Whether keyboard input is delivered to the editor.
     */
    val focused: Boolean
        get() = editor.config[Focused]

    /**
     * The editor's current caret position or selected text range.
     */
    val selection: TextRange
        get() = editor.config[TextSelectionRange]

    /**
     * Menu entries, including the choice that removes selection.
     */
    private val choices: List<SemanticsNode>
        get() = scene.semanticsNodes()
            .filter {
                it.config.getOrNull(SemanticsActions.OnClick) != null &&
                        it.config.getOrNull(EditableText) == null
            }

    /**
     * The number of currently offered choices; zero means that the menu is closed.
     */
    val choiceCount: Int
        get() = choices.size

    /**
     * Clicks the cross's slot, which remains in the layout when there is no selection.
     */
    fun clearSelection() {
        val bounds = editor.boundsInRoot
        // The cross precedes the arrow, with a gap between them and padding after the arrow.
        val inset = Filled.ArrowDropDown.defaultWidth + dimensions.spacingSmall * 2 +
                Filled.Clear.defaultWidth / 2
        scene.click(Offset(bounds.right - inset.value, bounds.center.y))
        scene.renderUntilIdle()
    }

    /**
     * Clicks the arrow to toggle the choice list.
     */
    fun openChoices() {
        val bounds = editor.boundsInRoot
        val inset = dimensions.spacingSmall + Filled.ArrowDropDown.defaultWidth / 2
        scene.click(Offset(bounds.right - inset.value, bounds.center.y))
        scene.renderUntilIdle()
    }

    /**
     * Opens the menu through keyboard input, requiring the selector to already have focus.
     */
    fun openChoicesWithKeyboard() {
        scene.pressKey(VK_DOWN)
        scene.releaseKey(VK_DOWN)
        scene.renderUntilIdle()
    }

    /**
     * Selects a visible menu item through pointer input.
     */
    fun selectChoice(index: Int) {
        scene.click(choices[index].boundsInRoot.center)
        scene.renderUntilIdle()
    }

    /**
     * Replaces the editor text through its accessible input action.
     */
    fun enterText(text: String) {
        onUiThread {
            checkNotNull(editor.config[SemanticsActions.SetText].action)
                .invoke(AnnotatedString(text))
        }
        scene.renderUntilIdle()
    }

    /**
     * Moves the caret through the editor's accessible selection action.
     */
    fun moveCaretTo(index: Int) {
        onUiThread {
            checkNotNull(editor.config[SemanticsActions.SetSelection].action)
                .invoke(index, index, false)
        }
        scene.renderUntilIdle()
    }
}

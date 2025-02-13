/*
 * Copyright 2025, TeamDev. All rights reserved.
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

import androidx.compose.foundation.layout.Arrangement.Start
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Represents a single page within the wizard.
 *
 * @see [Wizard]
 * @see [AbstractWizardPage]
 */
public interface WizardPage {

    /**
     * Defines the content of the page to display.
     */
    @Composable
    public fun content()

    /**
     * Validates the data entered on the page. In case of any validation
     * failure this method is expected to display the respective validation
     * error message(s), and focus the respective component if needed.
     *
     * @return `true` if the data is valid, `false` otherwise.
     */
    public fun validate(): Boolean

    /**
     * Switches the wizard's current page to this one.
     */
    public fun show()
}

/**
 * A base class for wizard page implementations.
 *
 * It simplifies implementing the functionality common to all pages.
 *
 * @param wizard
 *         a wizard to which the page belongs.
 */
public abstract class AbstractWizardPage(
    protected open val wizard: Wizard
) : WizardPage {

    override fun show() {
        wizard.currentPage = this
    }
}

/**
 * A text styled for displaying the wizard's section headers.
 *
 * @param text
 *         the text that should be displayed.
 */
@Composable
public fun SubheaderText(text: String) {
    Row(
        modifier = Modifier.padding(bottom = 16.dp),
        horizontalArrangement = Start
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Column layout for wizard's page input fields.
 *
 * @param padding
 *          padding to be applied to the column.
 * @param modifier
 *          [Modifier] to be applied to the column.
 */
@Composable
public fun InputColumn(
    padding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.padding(padding),
        verticalArrangement = spacedBy(16.dp)
    ) {
        content()
    }
}

/**
 * Row layout for wizard's page input fields.
 *
 * @param padding
 *         padding to be applied to the row.
 */
@Composable
public fun InputRow(
    padding: PaddingValues = PaddingValues(),
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(padding),
        horizontalArrangement = spacedBy(40.dp)
    ) {
        content()
    }
}

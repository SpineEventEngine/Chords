/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.layout

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

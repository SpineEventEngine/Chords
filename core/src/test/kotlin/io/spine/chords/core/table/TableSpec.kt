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

package io.spine.chords.core.table

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.spine.chords.core.ComponentSetup
import io.spine.chords.core.TestApplication
import io.spine.chords.core.layout.TestScene
import io.spine.chords.core.styling.ChordsTheme
import io.spine.chords.core.styling.chordsLightColorScheme
import io.spine.chords.core.table.testing.TableSortingScene
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Verifies table selection and separation of row actions from the scrollbar.
 */
@DisplayName("`Table` should")
internal class TableSpec {

    /**
     * Protects selected rows that move outside the viewport after resorting.
     */
    @Test
    fun `keep the selected entity visible after its sorting position changes`() {
        val initialEntities = (0 until EntityCount).map { entityId ->
            TableEntity(entityId, entityId)
        }
        val visibleEntityIds = mutableSetOf<Int>()
        val selectedEntity = mutableStateOf<TableEntity?>(null)
        var entities by mutableStateOf(initialEntities)
        TestScene(width = 300.dp, height = 300.dp) {
            val currentEntities = entities
            VisibilityTrackingTable {
                this.entities = currentEntities
                this.selectedEntity = selectedEntity
                this.visibleEntityIds = visibleEntityIds
            }
        }.use { scene ->
            repeat(4) {
                scene.click(x = 280.dp, y = 270.dp)
                scene.render()
            }
            val selectedEntityId = EntityCount - 3
            visibleEntityIds shouldContain selectedEntityId
            selectedEntity.value = initialEntities[selectedEntityId]
            scene.render()

            entities = initialEntities.map { entity ->
                if (entity.id == selectedEntityId) entity.copy(sortingPosition = -1)
                else entity
            }
            repeat(60) {
                scene.render()
            }

            visibleEntityIds shouldContain selectedEntityId
        }
    }

    /**
     * A scrollbar must have its own space even when an application increases its thickness.
     */
    @ParameterizedTest
    @ValueSource(ints = [8, 24])
    fun `keep row actions clear of the scrollbar`(scrollbarWidth: Int) {
        val scheme = chordsLightColorScheme()
        val entities = (0 until EntityCount).map { TableEntity(it, it) }
        var firstCellBounds = Rect.Zero
        TestScene(width = 300.dp, height = 160.dp) {
            ChordsTheme(colorScheme = scheme) {
                CompositionLocalProvider(
                    LocalScrollbarStyle provides LocalScrollbarStyle.current.copy(
                        thickness = scrollbarWidth.dp
                    )
                ) {
                    VisibilityTrackingTable {
                        this.entities = entities
                        selectedEntity = mutableStateOf(null)
                        contentPadding = PaddingValues()
                        columns = listOf(TableColumn(
                            name = "Value",
                            padding = PaddingValues()
                        ) { entity ->
                            Box(Modifier.fillMaxSize().onGloballyPositioned {
                                if (entity.id == 0) firstCellBounds = it.boundsInRoot()
                            })
                        })
                        enableRowActions()
                    }
                }
            }
        }.use { scene ->
            firstCellBounds.right shouldBe (300 - scrollbarWidth - 48).toFloat()
            val buttonCenter = Offset((300 - scrollbarWidth - 24).toFloat(), 60f)
            scene.movePointerTo(buttonCenter)
            scene.render()

            scene.pixelAt((buttonCenter.x - 8).dp, 60.dp) shouldBe
                    scheme.primaryContainer.toArgb()
            scene.pixelAt((300 - scrollbarWidth - 4).dp, 60.dp) shouldBe
                    scheme.primary.copy(alpha = 0.1f).compositeOver(scheme.surface).toArgb()
        }
    }

    /**
     * Reset restores the default order from either direction and preserves the selected row.
     */
    @ParameterizedTest
    @CsvSource("0, 1", "0, 2", "1, 1", "1, 2")
    fun `clear column sorting through the active header`(column: Int, clicks: Int) {
        TableSortingScene().use { scene ->
            scene.rowOrder shouldBe listOf(2, 1, 3)
            scene.canClear shouldBe false

            repeat(clicks) { scene.clickHeader(column) }

            val ascending = (column == 0) == (clicks == 1)
            scene.rowOrder shouldBe if (ascending) listOf(1, 2, 3) else listOf(3, 2, 1)
            scene.canClear shouldBe true

            scene.clear(usingKeyboard = false)

            scene.rowOrder shouldBe listOf(2, 1, 3)
            scene.canClear shouldBe false
            scene.selected shouldBe 1

            scene.clickHeader(column)
            scene.clear(usingKeyboard = true)

            scene.rowOrder shouldBe listOf(2, 1, 3)
            scene.canClear shouldBe false
            scene.selected shouldBe 1
        }
    }

    /**
     * Long headings must yield space to a small, square reset control without shrinking its icon.
     */
    @ParameterizedTest
    @ValueSource(ints = [240, 480])
    fun `keep the compact sorting reset round beside long headings`(width: Int) {
        TableSortingScene(width = width, heading = "Expiration date").use { scene ->
            scene.clickHeader(0)

            scene.clearButtonBounds.width shouldBe 20f
            scene.clearButtonBounds.height shouldBe 20f
            scene.clearIconBounds.width shouldBe 16f
            scene.clearIconBounds.height shouldBe 16f

            scene.clear(usingKeyboard = false)

            scene.rowOrder shouldBe listOf(2, 1, 3)
        }
    }

    /**
     * Narrow columns keep their complete headings readable with or without sorting controls.
     */
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `wrap long headings instead of truncating them`(sortable: Boolean) {
        val heading = "Long column heading"
        val wideHeight = TableSortingScene(
            width = 480,
            heading = heading,
            sortable = sortable
        ).use { scene ->
            if (sortable) scene.clickHeader(0)
            scene.headingBounds(0).height
        }

        TableSortingScene(width = 240, heading = heading, sortable = sortable).use { scene ->
            if (sortable) scene.clickHeader(0)

            scene.headingBounds(0).height shouldBeGreaterThan wideHeight
        }
    }

    /**
     * Installs the application that supplies shared component defaults.
     */
    private companion object {

        /**
         * The number of rows needed to make the table scroll vertically.
         */
        const val EntityCount: Int = 10

        /**
         * Initializes the application required by the component lifecycle.
         */
        @JvmStatic
        @BeforeAll
        fun setUpApplication() {
            TestApplication.install()
        }
    }
}

/**
 * A table that exposes which entity rows are currently composed.
 */
private class VisibilityTrackingTable : Table<TableEntity>() {

    /**
     * Declares remembered instances of this component.
     */
    companion object : ComponentSetup<VisibilityTrackingTable>({ VisibilityTrackingTable() })

    /**
     * The IDs of rows currently composed by the lazy list.
     */
    lateinit var visibleEntityIds: MutableSet<Int>

    init {
        defaultComparator = compareBy(TableEntity::sortingPosition)
        columns = listOf(TableColumn(name = "Value") { entity ->
            TrackVisibility(entity)
        })
    }

    /**
     * Supplies an action so layout checks include the trailing button column.
     */
    fun enableRowActions() {
        rowActions = RowActionsConfig(
            itemsProvider = { listOf(RowActionsItem("Action", {})) }
        )
    }

    /**
     * Returns the stable identity of the given row.
     */
    override fun extractEntityId(entity: TableEntity): Any = entity.id

    /**
     * Provides no content because the test always supplies entities.
     */
    @Composable
    override fun ColumnScope.EmptyTableContent() = Unit

    /**
     * Tracks the lifetime of the row composition for the given entity.
     */
    @Composable
    private fun TrackVisibility(entity: TableEntity) {
        DisposableEffect(entity.id) {
            visibleEntityIds.add(entity.id)
            onDispose {
                visibleEntityIds.remove(entity.id)
            }
        }
        Text(entity.id.toString())
    }
}

/**
 * A sortable row used to exercise stable table selection.
 */
private data class TableEntity(
    val id: Int,
    val sortingPosition: Int
)

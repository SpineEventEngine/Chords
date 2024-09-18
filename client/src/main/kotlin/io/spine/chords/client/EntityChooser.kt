/*
 * Copyright 2024, TeamDev. All rights reserved.
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

package io.spine.chords.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import io.spine.chords.appshell.app
import io.spine.base.EntityState
import io.spine.chords.DropdownSelector
import io.spine.chords.client.appshell.client
import io.spine.chords.runtime.MessageFieldValue
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.javaType

/**
 * A drop-down list, which allows the user to choose an entity among all
 * entities of a given type.
 *
 * This component allows the user to pick a respective item by looking it up
 * in the drop-down list associated with the component. This drop-down list
 * displays items for all respective entities of a given type [E], and allows
 * the user to filter this list by entering a search substring within the
 * component's input field.
 *
 * Like any [InputComponent][io.spine.chords.InputComponent], this
 * component identifies the currently selected item using its [value] property.
 * It should be noted that the [value] property contains the entity's ID (of
 * type [I]), and not the entity state of type [E] itself, which is just used
 * internally within the component.
 *
 * In order to implement a concrete component for choosing entities of some
 * particular type, the component's implementation that extends this class has
 * to specify the type of items to be searched for by implementing the
 * [entityStateClass] method, and implement [entityId] and [itemText] methods.
 *
 * @param I a type of entity ID.
 * @param E a type of entity state.
 */
@Stable
public abstract class EntityChooser<
        I : MessageFieldValue,
        E : EntityState // Keep in sync with the `TypeParameters` enum!
        > : DropdownSelector<I>() {

    /**
     * An enumeration of class's type parameters.
     */
    @Suppress("unused" /* All type parameters are listed for
    completeness despite only a part of them might be used via this enum. */)
    private enum class TypeParameters(val index: Int) {

        /**
         * The entity ID type parameter (`I` class's parameter).
         */
        ID(0),

        /**
         * The entity state type parameter (`E` class's parameter).
         */
        ENTITY_STATE(1)
    }

    override val items: MutableState<Iterable<I>> = mutableStateOf(listOf())

    private val entityStates = run {
        val entityStates = mutableStateOf(listOf<E>())
        app.client.readAndObserve(entityStateClass, entityStates, ::entityId)
        entityStates
    }

    private val entityStatesByIds: HashMap<I, E> = hashMapOf()

    /**
     * A function which has to be implemented to provide a value of
     * type `Class[E]` for the component which is being implemented.
     *
     * E.g., if [E] is a `User` class, it would just specify `User::class.java`.
     */
    @OptIn(ExperimentalStdlibApi::class)
    private val entityStateClass: Class<E> get() {
        val parameterizedEntityChooserType = this::class.allSupertypes.first {
            it.classifier == EntityChooser::class
        }
        val entityStateType =
            parameterizedEntityChooserType.arguments[TypeParameters.ENTITY_STATE.index].type
        checkNotNull(entityStateType) {
            "The V type parameter (entity value type) cannot be a star <*> projection. "
        }

        // We only have the entity state type as an abstract `Type` value at
        // runtime, so we have no other choice than to explicitly cast it to
        // Class<E> here (it's safe as long as `TypeParameters` enum is kept
        // up to date).
        @Suppress("UNCHECKED_CAST")
        return entityStateType.javaType as Class<E>
    }

    /**
     * Returns the unique identifier of the given entity.
     *
     * @param entityState
     *         an entity state whose unique identifier should be returned.
     * @return a unique identifier field of the given [entityState].
     */
    protected abstract fun entityId(entityState: E): I

    /**
     * A function, which has to be implemented to provide a text representation
     * for the given entity's item.
     *
     * Item text representations are used for displaying item contents in
     * the list, and define the text by which items can be searched/filtered by
     * the user within the component's input field.
     *
     * @param entityId
     *         an ID of an entity displayed in this item.
     * @param entityState
     *         an entity displayed in the item whose text representation should
     *         be obtained.
     * @return an item's text representation.
     */
    protected abstract fun itemText(entityId: I, entityState: E?): String?

    /**
     * Given an entity ID, returns the respective entity state.
     *
     * @param entityId
     *         ID of an entity whose state should be obtained.
     * @return an entity state with the specified ID.
     */
    protected fun entityById(entityId: I): E? = entityStatesByIds[entityId]

    /**
     * Renders the composable content that should be displayed in a drop-down
     * list item identified by the provided parameters.
     *
     * This function can be overridden to customize what is displayed within
     * each drop-down list item. By default, this method displays the item's
     * text representation as defined by the [entityItemText] function, with
     * highlighting its portion that matches the [searchString] entered by
     * the user.
     *
     * @param entityId
     *         ID of an item whose content should be rendered.
     * @param entityState
     *         an entity state that corresponds to the item being rendered.
     * @param entityItemText
     *         an entity item's text representation as defined by
     *         the [itemText] function.
     */
    @Composable
    protected open fun itemContent(entityId: I, entityState: E?, entityItemText: String) {
        super.itemContent(entityId, entityItemText)
    }

    // A changed parameter name reflects a more concrete meaning of this
    // parameter in this component.
    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun itemText(entityId: I): String {
        val entityState = entityById(entityId)
        return itemText(entityId, entityState) ?: ""
    }

    @Composable
    // A changed parameter name reflects a more concrete meaning of this
    // parameter in this component.
    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun itemContent(entityId: I, entityItemText: String) {
        val entityState = entityById(entityId)
        itemContent(entityId, entityState, entityItemText)
    }

    @Composable
    @ReadOnlyComposable
    override fun beforeComposeContent() {
        super.beforeComposeContent()
        entityStatesByIds.clear()
        entityStates.value.forEach {
            entityStatesByIds[entityId(it)] = it
        }
        items.value = entityStates.value.map { entityId(it) }
    }
}

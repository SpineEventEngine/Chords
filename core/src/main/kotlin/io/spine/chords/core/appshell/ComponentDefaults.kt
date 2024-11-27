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

package io.spine.chords.core.appshell

import io.spine.chords.core.Component
import io.spine.chords.core.ComponentProps
import kotlin.reflect.KClass

/**
 * Defines the type of declarations that can be used within
 * [Application.componentDefaults] implementations.
 */
public interface ComponentDefaultsScope {

    /**
     * Registers default property values that should be applied to all
     * components of class [C] and its subclasses.
     *
     * For each component that is an instance of [C] or its
     * subclass, the provided [props] lambda will be invoked.
     *
     * @receiver A class of a component whose default property
     *   values need to be specified. Specifying a type `C` means that
     *   the property values specified in this call will be set for all
     *   components that are either of exact same class, or any of
     *   its subclasses.
     * @param props A lambda, which is invoked in context of each component of
     *   class [C] (the component is referred by `this`), and which is expected
     *   to assign default property values for such a component.
     */
    public infix fun <C : Component> KClass<out C>.defaultsTo(props: ComponentProps<C>)
}

/**
 * An implementation of [ComponentDefaultsScope], which serves as a registry for
 * component defaults that were configured to be applicable across the entire
 * application, and provides an API that allows to apply the default properties
 * to components.
 */
internal class ComponentDefaults : ComponentDefaultsScope{

    /**
     * The raw data about which properties are registered for which types of
     * components (without including properties registered for parent classes).
     *
     * This data structure serves as a source of truth about default properties,
     * but it is not optimized for quick access per se.
     *
     * @see componentConfiguratorsPrepared
     */
    private val componentConfiguratorsRaw:
            MutableMap<Class<*>, List<ComponentProps<*>>> = HashMap()

    /**
     * A cache of per-component-type property initialization lambdas that were
     * derived from [componentConfiguratorsRaw] to represent a quickest possible
     * way of setting all properties available for a certain component type.
     *
     * Unlike [componentConfiguratorsRaw], the per-component-type property
     * assignment lambdas here contain property initializers that were
     * registered for all parent classes as well.
     *
     * @see componentConfiguratorsRaw
     */
    private val componentConfiguratorsPrepared:
            MutableMap<Class<*>, ((Component) -> Unit)?> = HashMap()

    override infix fun <C : Component> KClass<out C>.defaultsTo(
        props: ComponentProps<C>
    ) {
        val componentClass: Class<out C> = this.java
        @Suppress(
            // Components are stored by the parent class internally.
            "UNCHECKED_CAST"
        )
        val initializerForClass = componentConfiguratorsRaw.getOrPut(
            componentClass as Class<*>, { ArrayList<ComponentProps<C>>() }
        ) as MutableList<ComponentProps<C>>

        initializerForClass += props
    }

    /**
     * Given a component class, returns a function that applies any defaults
     * that are applicable to a component of that type.
     *
     * Implementation note: the lambda returned by this method should be
     * constructed in a most performance-effective way (e.g. precache
     * the results of any map lookups, and minimize any computationally costly
     * operations within the returned lambda as much as possible in general).
     *
     * @param componentClass A class of component whose default properties
     *   initializer needs to be obtained.
     * @return A lambda, which, given a component instance of type [C], assigns
     *   default property values to it and its parent classes, or `null` if no
     *   default property values are declared for the [componentClass] and any
     *   of its parent classes.
     */
    fun <C : Component> componentDefaultsInitializer(componentClass: Class<C>): ((C) -> Unit)? {
        val initializers: ((Component) -> Unit)? = componentConfiguratorsPrepared.getOrPut(
            componentClass
        ) {
            val initializers: java.util.ArrayList<ComponentProps<C>> = ArrayList()
            var cls: Class<*>? = componentClass
            while (cls != null) {
                @Suppress(
                    // Components are stored by the parent class internally.
                    "UNCHECKED_CAST"
                )
                val props = componentConfiguratorsRaw[cls] as List<ComponentProps<C>>?
                if (props != null) {
                    initializers.addAll(props)
                }
                cls = cls.superclass
            }

            // Make sure parent classes are applied first.
            initializers.reverse()

            if (initializers.isEmpty()) {
                null
            } else {
                val result: (Component) -> Unit = { component ->
                    @Suppress(
                        // Components are stored by the parent class internally.
                        "UNCHECKED_CAST"
                    )
                    with(component as C) {
                        for (initializer: ComponentProps<C> in initializers) {
                            initializer.run { configure() }
                        }
                    }
                }
                result
            }
        }
        return initializers
    }
}

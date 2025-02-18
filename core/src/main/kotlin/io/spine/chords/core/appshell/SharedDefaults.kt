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

package io.spine.chords.core.appshell

import io.spine.chords.core.Component
import io.spine.chords.core.ComponentSetup
import io.spine.chords.core.DefaultPropsOwner
import kotlin.reflect.KClass

/**
 * A functional interface that defines the signature for user-provided
 * component's property configuration functions.
 *
 * It generally doesn't need to be used directly when using the components,
 * since it would be implicitly created by a lambda that is passed to the
 * [ComponentSetup.invoke] function.
 * It is a part of an internal implementation of [Component], and, in case of
 * some advanced components, can also be used when creating new components.
 *
 * @See Component
 * @see ComponentSetup.invoke
 * @see Component.props
 */
public fun interface Props <O: DefaultPropsOwner> {

    /**
     * The function that, given a component's instance in its receiver, is
     * expected to contain component instance property value
     * declarations (assignments).
     *
     * @receiver a component whose properties are to be configured.
     */
    public fun O.configure()
}
/**
 * Defines the type of declarations that can be used within
 * [Application.sharedDefaults] implementations.
 */
public interface SharedDefaultsScope {

    /**
     * Registers default property values that should be applied to all
     * components of class [O] and its subclasses.
     *
     * For each component that is an instance of [O] or its
     * subclass, the provided [props] lambda will be invoked.
     *
     * @receiver A class of a component whose default property
     *   values need to be specified. Specifying a type `C` means that
     *   the property values specified in this call will be set for all
     *   components that are either of exact same class, or any of
     *   its subclasses.
     * @param props A lambda, which is invoked in context of each component of
     *   class [O] (the component is referred by `this`), and which is expected
     *   to assign default property values for such a component.
     */
    public infix fun <O : DefaultPropsOwner> KClass<out O>.defaultsTo(props: Props<O>)
}

/**
 * An implementation of [SharedDefaultsScope], which serves as a registry for
 * component defaults that were configured to be applicable across the entire
 * application, and provides an API that allows to apply the default properties
 * to components.
 */
internal class SharedDefaults : SharedDefaultsScope {

    /**
     * The raw data about which properties are registered for which types of
     * components (without including properties registered for parent classes).
     *
     * This data structure serves as a source of truth about default properties,
     * but it is not optimized for quick access per se.
     *
     * @see propConfiguratorsPrepared
     */
    private val propConfiguratorsRaw:
            MutableMap<Class<*>, List<Props<*>>> = HashMap()

    /**
     * A cache of per-component-type property initialization lambdas that were
     * derived from [propConfiguratorsRaw] to represent a quickest possible
     * way of setting all properties available for a certain component type.
     *
     * Unlike [propConfiguratorsRaw], the per-component-type property
     * assignment lambdas here contain property initializers that were
     * registered for all parent classes as well.
     *
     * @see propConfiguratorsRaw
     */
    private val propConfiguratorsPrepared:
            MutableMap<Class<*>, ((DefaultPropsOwner) -> Unit)?> = HashMap()

    override infix fun <O : DefaultPropsOwner> KClass<out O>.defaultsTo(
        props: Props<O>
    ) {
        val componentClass: Class<out O> = this.java
        @Suppress(
            // Components are stored by the parent class internally.
            "UNCHECKED_CAST"
        )
        val initializerForClass = propConfiguratorsRaw.getOrPut(
            componentClass as Class<*>, { ArrayList<Props<O>>() }
        ) as MutableList<Props<O>>

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
     * @param forClass A class of component whose default properties
     *   initializer needs to be obtained.
     * @return A lambda, which, given a component instance of type [O], assigns
     *   default property values to it and its parent classes, or `null` if no
     *   default property values are declared for the [forClass] and any
     *   of its parent classes.
     */
    fun <O : DefaultPropsOwner> defaultsInitializer(forClass: Class<O>): ((O) -> Unit)? {
        val initializers: ((DefaultPropsOwner) -> Unit)? = propConfiguratorsPrepared.getOrPut(
            forClass
        ) {
            val initializers: java.util.ArrayList<Props<O>> = ArrayList()
            var cls: Class<*>? = forClass
            while (cls != null) {
                @Suppress(
                    // Configurators are stored by the parent class internally.
                    "UNCHECKED_CAST"
                )
                val props = propConfiguratorsRaw[cls] as List<Props<O>>?
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
                val result: (DefaultPropsOwner) -> Unit = { propsOwner ->
                    @Suppress(
                        // Configurators are stored by the parent class internally.
                        "UNCHECKED_CAST"
                    )
                    with(propsOwner as O) {
                        for (initializer: Props<O> in initializers) {
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

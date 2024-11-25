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

public interface ComponentDefaultsScope {
    public fun <C : Component> component(componentClass: KClass<out C>, props: ComponentProps<C>)
}

public class ComponentDefaults : ComponentDefaultsScope{
    private val componentConfiguratorsRaw:
            MutableMap<Class<*>, List<ComponentProps<*>>> = HashMap()
    private val componentConfiguratorsPrepared:
            MutableMap<Class<*>, ((Component) -> Unit)?> = HashMap()

    override fun <C : Component> component(
        componentClass: KClass<out C>,
        props: ComponentProps<C>
    ) {
        val initializerForClass = componentConfiguratorsRaw.getOrPut(
            componentClass.java as Class<*>, { ArrayList<ComponentProps<C>>() }
        ) as MutableList<ComponentProps<C>>
        initializerForClass += props
    }

    public fun <C : Component> componentInitializer(componentClass: Class<C>): ((C) -> Unit)? {
        val initializers: ((Component) -> Unit)? = componentConfiguratorsPrepared.getOrPut(
            componentClass
        ) {
            val initializers: java.util.ArrayList<ComponentProps<C>> = ArrayList()
            var cls: Class<*>? = componentClass
            while (cls != null) {
                val props = componentConfiguratorsRaw[cls] as List<ComponentProps<C>>?
                if (props != null) {
                    initializers.addAll(props)
                }
                cls = cls.superclass
            }

            if (initializers.isEmpty()) {
                null
            } else {
                val result: (Component) -> Unit = { component ->
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

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

package io.spine.chords.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * A base class for class-based component implementations.
 *
 * A notion of class-based components that is introduced with this class
 * provides an alternative way for implementing components with utilizing
 * the features of the object-oriented paradigm.
 *
 * ## Using class-based components
 *
 * The syntax for using such components is similar to that of using the regular
 * function-based components. Considering a function-based component declaration
 * like this:
 *
 * ```kotlin
 *     SomeComponent(
 *         parameter1 = value1,
 *         parameter2 = value2,
 *         ...
 *     )
 * ```
 *
 * The usage of an analogous class-based component would look like this:
 *
 * ```kotlin
 *     SomeComponent {
 *         property1 = value1
 *         property2 = value2
 *         ...
 *     }
 * ```
 *
 * Such a component usage expression is called a _component's instance
 * declaration_. It both lazily creates and remembers a component's instance for
 * this invocation site, and also renders the component (similar to regular
 * function-based components).
 *
 * The lambda passed in such a declaration is invoked before each
 * recomposition of the component, which, similar to function-based components,
 * results in each component's recomposition to run with the up-to-date
 * parameters (property values).
 *
 * A respective component instance is cached using [remember] for each of such
 * respective component's usage sites, so the same instance will be reused for
 * each respective declaration. You can obtain a reference to a component's
 * instance as follows, if needed:
 *
 * ```kotlin
 *     val someComponent = SomeComponent { ... }
 * ```
 *
 * ### Component instance declarations vs constructors
 *
 * It should be noted that such _component instance declarations_ as shown above
 * are a main way of using class-based components, but they are technically not
 * the same as just instantiating components using their constructor.
 *
 * A component's constructor would actually not need to be used directly in most
 * cases! Instead of using the constructor, such expressions work thanks to
 * the [invoke][ComponentSetup.invoke] operator function being declared
 * on the component's companion object, which is in particular required to
 * prevent creating a new component's instance upon each composition, and use
 * a cached instance instead.
 *
 * ### Preferred instance declarations style
 *
 * Note that the component instance declaration examples above are technically
 * a shorthand notation for using the `invoke` function on the component's
 * companion object explicitly. So, theoretically, the same examples could be
 * written like this:
 *
 * ```kotlin
 *     SomeComponent.Companion.invoke({
 *         property1 = value1
 *         property2 = value2
 *         ...
 *     })
 * ```
 *
 * ...or like this:
 *
 * ```kotlin
 *     SomeComponent.invoke {
 *         property1 = value1
 *         property2 = value2
 *         ...
 *     }
 * ```
 *
 * Nevertheless, such explicit usage of `Companion` or `invoke` is not
 * recommended, and a shorthand form is recommended instead:
 *
 * ```kotlin
 *     SomeComponent {
 *         property1 = value1
 *         property2 = value2
 *         ...
 *     }
 * ```
 *
 * NOTE: It is strongly recommended to use the property configuration lambda
 * in component declarations only for performing component property assignments,
 * which is conceptually similar to how you would pass respective parameters to
 * a function-based component.
 *
 * See also below how to implement such components.
 *
 * ## Implementing class-based components
 *
 * - Create a subclass of [Component].
 *
 * - Add a companion object of type [ComponentSetup], which introduces
 *   the instance declaration API (which is technically being an invocation).
 *
 *   You can consider the presence of this companion object as a kind of
 *   replacement for a public constructor (albeit it actually both ensures
 *   a lazy component's creation, and its rendering at the same time).
 *
 *   Note: abstract base components do not need this companion object
 *   (since they would never be instantiated and used by themselves).
 *
 * - Implement the composable [content] method to have the component's
 *   rendering code — any respective component's composable content that should
 *   be displayed in the component, just like implementing regular
 *   composable functions.
 *
 * - Declare a mutable property for each "parameter" of your component.
 *
 * Here's an example of creating an input component that allows entering
 * a string value:
 *
 * ```kotlin
 *     public class HelloComponent : Component() {
 *         public companion object : ComponentSetup<HelloComponent>({
 *             HelloComponent()
 *         })
 *
 *         public var name: String = ""
 *
 *         @Composable
 *         override fun content() {
 *             Text("Hello, $name")
 *         }
 *     }
 * ```
 *
 * Such a component can be used like this:
 * ```kotlin
 *     HelloComponent { name = "User" }
 * ```
 *
 * A note on code style: despite Kotlin's
 * [code conventions](https://kotlinlang.org/docs/coding-conventions.html#class-layout)
 * guide, which recommends having companion objects at the very end of class
 * declarations, Chords libraries adopt and recommend a rule of having
 * `ComponentSetup` based companion objects in component classes as the very
 * first declaration in the class instead.
 *
 * Such placement is considered acceptable *just as an exception for component
 * classes* though, since companion objects here serve the purpose that is
 * similar to a class's constructor.
 *
 * ## When to write class-based and function-based components?
 *
 * A class-based components writing style is only a convenience that can be used
 * if it provides some benefits relative to function-based ones. Function-based
 * components can still be written whenever that seems
 * more convenient/appropriate.
 *
 * Both paradigms are mutually compatible: functional components can be used in
 * class-based ones, and class-based ones can be used in functional ones.
 *
 * ## Benefits of writing class-based components
 *
 * A class-based component implementation provides the following benefits
 * in particular:
 *
 * - **Implicit component instance creation**. A component's instance is
 *   implicitly created and remembered for every component's declaration
 *   (usage) site.
 *
 * - **Support for imperative API**. Introducing the notion of component
 *   instances makes it trivial to create imperative parts of component's API.
 *   For example things like focusing a component (see
 *   the [focus][InputComponent.focus] function) can now be made in a more
 *   straightforward style than the classical Compose's way
 *   (see `Modifier.focusRequester()`).
 *
 *   ```kotlin
 *       someComponent.focus()
 *   ```
 *
 * - The component's implementation can place any data that needs to be
 *   remembered across recompositions into component's private properties
 *   instead of declaring the [remember] section to store each of such values.
 *
 * - **Reuse and standardization of common concepts**. It becomes possible to
 *   easily standardize the concepts common for all similar components
 *   (e.g. concepts reused in all input components) by extracting them into base
 *   class(es). For example, the input component's parameters such as its value,
 *   validity state ([valueValid][InputComponent.valueValid]), external
 *   validation message
 *   ([externalValidationMessage][InputComponent.externalValidationMessage]),
 *   etc. can be declared and documented as properties in the respective base
 *   class without such things in all components.
 *
 * - **More compact definition of component hierarchies**.
 *
 *   In a purely functional style multi-level component hierarchies are
 *   typically implemented with composition involving a notable amount of
 *   "bureaucracy" for passing many component's parameters to their "nested"
 *   components.
 *
 *   Class inheritance would implicitly make all parent's properties available
 *   to descendant implementations (no need for many explicit parameter
 *   specifications like `param1 = param1` in such cases).
 *
 * - **Encapsulation benefits**: clear implementation boundaries, implicit access
 *   to all properties in all functions.
 *
 *   If a component requires some extra functions for its functioning, it's easy
 *   to declare them as component class's methods, which makes component's
 *   implementation boundaries more explicit, and also makes all component's
 *   data (stored in its properties) to be implicitly available in each of such
 *   functions without explicitly passing them around with parameters.
 *
 * ## Converting function-based components into class-based ones
 *
 * The points below can be used as a rule of thumb when converting existing
 * function-based components to class-based ones. This can also be helpful for
 * understanding the differences between the two component writing paradigms.
 *
 * - A class should be created instead of a composable function. Its name is
 *   generally expected to be the same as the function that is being converted.
 *
 * - This class should extend the [Component] class (or some of its subclasses).
 *
 * - Each parameter of a component (which would be passed as a parameter to
 *   a function-based component), should be declared as a mutable public
 *   property `public var ...`.
 *
 * - The actual composable content of the component (content of the composable
 *   function that is being transformed into a class-based component) is
 *   placed inside an overridden [content] method (which is declared as
 *   a `@Composable` one as well).
 *
 * - Class-based components are typically never instantiated directly via their
 *   constructor (see "Using class-based components")! Instead, they are
 *   technically "invoked" via an `invoke` operator on their companion object.
 *   To ensure this usage syntax, the following should be done:
 *
 *   - Add a public companion object that extends `ComponentCompanion` (or its
 *     subclass, e.g. if you're extending a subclass of `Component`).
 *
 *   See the details in the "Implementing class-based components" section above.
 *
 * - Consider transforming variables whose values are cached with [remember]
 *   in a function-based component to be private or protected class's
 *   properties instead.
 *
 *   You can still use `remember` if needed, but please note that the whole
 *   component's instance is already automatically `remember`-ed, which means
 *   that you can often just store such data in its properties in such cases.
 *
 * - If your composable components has some nested functions and/or big lambdas,
 *   consider transforming them into methods of the component's class.
 *
 * - Likewise, if there are any additional functions that you consider to be
 *   a part of the component, consider making them to be methods of
 *   the component's class.
 *
 *   This can especially be appropriate if you had to pass some of the variables
 *   with `remember`-ed values into such functions. Transforming such functions
 *   into class methods would make respective class's properties to be available
 *   to such methods implicitly.
 *
 * ## Optimizing performance
 *
 * These recommendations are optional, but can be considered in cases when UI's
 * performance becomes an issue for some components or when you're creating
 * components which are going to be widely reusable.
 *
 * Just like for any other composable function, Compose decides whether to
 * invoke the [Component.content] function along with the parent's composition
 * scope depending on the stability of the composable function's parameters (see
 * [Compose's documentation](https://developer.android.com/develop/ui/compose/performance/stability)).
 * In case of the [Component.content] function, it does not have any other
 * parameters than the implicit `this` parameter, which refers to the actual
 * [Component]'s implementation instance.
 *
 * The base [Component] class and other major base classes in the component's
 * hierarchy, such as [InputComponent] are designed to satisfy the requirements
 * to be considered [Stable] types, which minimizes the number of component's
 * recompositions ([Component.content] invocations).
 *
 * It should be noted that depending on how the actual [Component]'s subclass
 * is implemented may prevent the component type from being considered stable,
 * and can cause excessive recompositions ([Component.content] invocations) to
 * happen for the component, which can affect the UI's (responsiveness) in some
 * cases. When performance degradation due to excessive recompositions needs
 * to be optimized, ensure that the component's implementation forms a type,
 * which is considered
 * [stable](https://developer.android.com/develop/ui/compose/performance/stability#types)
 * by the Compose's definition.
 *
 * Here are some considerations that might be helpful for adapting an unstable
 * component's implementation to be a stable one:
 * - Any component that has any `var` property or any `val` property whose type
 *   is unstable and not immutable is considered as an unstable type by
 *   Compose by default.
 * - Since such properties are naturally required by many component's
 *   implementations, making a component stable would require adding
 *   a [Stable] annotation to the component's class.
 * - If any component (as well as any other type) has a [Stable] annotation, its
 *   implementation has to adhere to the contract of stable types (see the
 *   [Stable] annotation's description). In practice this might require such
 *   techniques as:
 *   - Ensuring that a component doesn't have any public unstable properties.
 *   - Making sure that public `var` properties are backed by `MutableState` to
 *     ensure that changing the property triggers the component's recomposition,
 *     e.g. like this:
 *     ```
 *        public var name: String by mutableStateOf("")
 *     ```
 * - Primitive types and lambdas are considered stable.
 *
 * See the [Stability in Compose](https://developer.android.com/develop/ui/compose/performance/stability#types)
 * documentation for details.
 *
 * @constructor A constructor, which is used internally by the component's
 *   implementation (its companion object). As an application developer, use
 *   [companion object][ComponentSetup]'s [invoke][ComponentSetup.invoke]
 *   operator for instantiating and rendering any specific
 *   component's implementation.
 */
@Stable
public abstract class Component {

    /**
     * A callback, which configures component's properties according to the
     * needs of actual component's application usage scenario.
     *
     * In most cases this property would not need to be used by the
     * application's code directly, since it would be set automatically by
     * [ComponentSetup.invoke] or an analogous component
     * declaration function.
     */
    public var props: ComponentProps<Component>? = null

    /**
     * A state variable which specifies whether the component's [initialize]
     * method has been called.
     */
    private val initialized = mutableStateOf(false)

    /**
     * A component's lifecycle method, which is invoked right after the
     * [props] callback (which is passed along with component
     * instance's declaration) has been invoked for the first time, and before
     * the component's composable content is rendered for the first time.
     *
     * This function is declared as being a read-only composable one for
     * the implementations to be able to access the composition-related data,
     * such as various [MaterialTheme][androidx.compose.material3.MaterialTheme]
     * properties and other
     * [CompositionLocal][androidx.compose.runtime.CompositionLocal] values.
     */
    protected open fun initialize() {
        check(!initialized.value) {
            "Component.initialize() shouldn't be invoked more than once."
        }
    }

    /**
     * Updates property values and renders the composable content that
     * represents this component.
     *
     * NOTE: in most cases this method is not expected to be invoked or
     * overridden by application developers, and is used internally within
     * the component's implementation.
     *
     * - Instead of invoking this method explicitly, the component instance
     *   declaration expressions take care of the standard component's
     *   lifecycle, including instance creation, property updates, and
     *   rendering, which removes the need to perform any of those steps
     *   explicitly. See the "Using class-based components" section in
     *   [Component] class description, and the [ComponentSetup.invoke]
     *   operator functions for details.
     *
     * - The component's composable content has to be specified by overriding
     *   the [content] method.
     */
    @Composable
    public open fun Content(): Unit = recompositionWorkaround {
        props?.run { configure() }
        if (!initialized.value) {
            initialize()
            initialized.value = true
        }

        beforeComposeContent()
        content()
    }

    /**
     * This method is invoked before each component's recomposition (right
     * before invoking [content]).
     *
     * The default implementation updates component's properties to have their
     * respective configured values actual at this point in time.
     *
     * This function is declared as being a read-only composable one for
     * the implementations to be able to access the composition-related data,
     * such as various [MaterialTheme][androidx.compose.material3.MaterialTheme]
     * properties and other
     * [CompositionLocal][androidx.compose.runtime.CompositionLocal] values.
     */
    @Composable
    @ReadOnlyComposable
    protected open fun beforeComposeContent(): Unit = recompositionWorkaroundReadonly {
    }

    /**
     * A method that is responsible for rendering the composable content that
     * represents this component.
     *
     * By the time this method is invoked, component's properties are updated
     * according to the property value specifications provided along with
     * the component instance's declaration.
     */
    @Composable
    protected abstract fun content()
}

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
public fun interface ComponentProps <C: Component> {

    /**
     * The function that, given a component's instance in its receiver, is
     * expected to contain component instance property value
     * declarations (assignments).
     *
     * @receiver a component whose properties are to be configured.
     */
    public fun C.configure()
}

/**
 * A base abstract class for components companion objects, which provides
 * the basic functionality behind declaring component instances.
 *
 * See the "Using class-based components" and "Implementing class-based
 * components" sections of the [Component] class for general information about
 * how class-based components are used in an application.
 *
 * In most cases custom class-based components would use [ComponentSetup]
 * for to introduce the component's _instance declaration API_. However, in some
 * rare case a component might require different instance declaration API. In
 * such cases those companion objects would use this class as a base class for
 * a companion object instead.
 *
 * @param createInstance A lambda that should create a component's instance.
 * @see ComponentSetup
 */
public abstract class AbstractComponentSetup(
    protected val createInstance: (() -> Component)? = null
) {

    /**
     * Creates a component of type [C] lazily with caching its instance using
     * [remember] for this invocation site, and renders its content using
     * the [content] callback.
     *
     * @props props A lambda, which, given a component's instance, configures
     *   its properties according to client's requirements.
     * @props content A lambda, which, given a component's instance, renders
     *   the composable content for that component.
     * @return A component's instance that was created and cached for this
     *   declaration site.
     */
    @Composable
    public fun <C : Component> createAndRender(
        props: ComponentProps<C>? = null,
        createInstance: (() -> C)? = null,
        content: @Composable C.() -> Unit
    ): C {
        val instance: C = remember { create(createInstance, props) }

        /* We're not adding an extra component's type parameter that would
           refer to itself to simplify the component authoring experience,
           hence we're doing an explicit typecast here as a compromise */
        @Suppress("UNCHECKED_CAST")
        instance.props = props as ComponentProps<Component>?
        content(instance)
        return instance
    }

    protected fun <C : Component> create(
        createInstance: (() -> C)? = null,
        config: ComponentProps<C>? = null
    ): C {
        lateinit var instance: C

        // Cannot use the class-level type parameter due to complexities
        // with some subclasses that have type parameters (e.g., MessageForm).
        @Suppress("UNCHECKED_CAST")
        instance = checkNotNull(
            (this.createInstance ?: createInstance)?.invoke()
        ) {
            "Either constructor's or `create` function's `createInstance` " +
                    "parameter must be specified as a non-null value."
        } as C

        /* We're not adding an extra component's type parameter that would
           refer to itself to simplify the component authoring experience,
           hence we're doing an explicit typecast here as a compromise */
        @Suppress("UNCHECKED_CAST")
        instance.props = config as ComponentProps<Component>?
        return instance
    }
}

/**
 * A companion object type that introduces a component's instance
 * declaration API.
 *
 * See the "Using class-based components" and "Implementing class-based
 * components" sections of the [Component] class for general information about
 * how class-based components are used in an application. An important point
 * here is that to make an experience of using class-based components to be
 * similar to that of function-based components, components are typically never
 * instantiated via their constructor directly, and are instead used "declared"
 * in a familiar invocation style. A companion object like this is what
 *
 * This is a base class that should normally be used for a companion object of
 * any non-abstract class-based component (a direct or indirect descendant of
 * [Component]), to provide a top-level API for component instance
 * declaration expressions.
 *
 * Please note that component's constructor(s) themselves shouldn't be used
 * directly for instantiating components, and are considered to be internal to
 * the component's implementation (technically being `private` or `protected`).
 * Instead of this, component instances should be _declared_ using one of
 * the [invoke] operators on the respective component's companion object.
 *
 * Once this type is added as a companion object to some respective component
 * type (e.g., `SomeComponent` for an example), an instance of the respective
 * component can be declared like this:
 *
 * ```
 *     SomeComponent {
 *         property1 = value1
 *         property2 = value2
 *         ...
 *     }
 * ```
 *
 * Such an invocation both creates an instance of the component (if it hasn't
 * been created for this particular declaration yet), and renders this component
 * in the composable context where it is declared.
 *
 * The component's instance is created lazily and is cached with the same
 * semantics that is used by the [remember] function.
 *
 * Note: unlike the default recommended
 * [class layout](https://kotlinlang.org/docs/coding-conventions.html#class-layout),
 * this companion object is typically fine to declare as the first class's
 * member, since it's conceptually similar to the class's constructor.
 *
 * @param C A type of the component that owns this companion object (and which
 *   should be instantiated and rendered via this companion object).
 *
 * @constructor Creates a companion object for a component of type [C].
 * @param createInstance A lambda that should create a component's instance of
 *   type [C] with the given properties configuration callback.
 * @see AbstractComponentSetup
 */
public open class ComponentSetup<C: Component>(
    createInstance: () -> C
) : AbstractComponentSetup(createInstance) {

    /**
     * Declares an instance of component of type [C] with the respective
     * property value specifications.
     *
     * More precisely, this lazily creates an instance of the component of type
     * [C] (to which this companion object belongs), and renders this component
     * in the composable context where this invocation is made.
     *
     * Once an instance is created, it is saved using [remember] and is reused
     * for subsequent recompositions.
     *
     * @param props A lambda that is invoked in context of the component's
     *   instance, which should configure its properties in a way that is needed
     *   for this component's instance. It is invoked before each recomposition
     *   of the component.
     * @return A component's instance that has been created or cached for this
     *   declaration site.
     */
    @Composable
    public operator fun invoke(
        props: ComponentProps<C>
    ): C = createAndRender(props) {
        Content()
    }
}

/**
 * This function can be used to work around what appears to be a bug in Compose,
 * where an internal Compose's exception is thrown during recompositions of
 * overridden `open` composable methods.
 *
 * ### What the bug looks like
 *
 * The issue manifests itself by throwing an exception like this:
 * ```
 *     Exception in thread "AWT-EventQueue-0" androidx.compose.runtime.ComposeRuntimeError:
 *     Compose Runtime internal error. Unexpected or incorrect use of the Compose
 *     internal runtime API (Cannot seek outside the current group (101-105)).
 *     Please report to Google or use https://goo.gle/compose-feedback
 * ```
 *
 * Another variant of the message in parentheses above that was seen in
 * apparently the same circumstances looked like this:
 * ```
 *     Cannot reposition while in an empty region
 * ```
 *
 * The bug has been reported here (for Compose Multiplatform):
 *   [https://github.com/JetBrains/compose-multiplatform/issues/4491](https://github.com/JetBrains/compose-multiplatform/issues/4491),
 * and also a duplicate was reported here (for Jetpack Compose):
 *   [https://issuetracker.google.com/issues/329477544](https://issuetracker.google.com/issues/329477544)
 *
 * A similar (likely the same) issue was also found to be reported for Jetpack
 * Compose here:
 *   [https://issuetracker.google.com/issues/254292974](https://issuetracker.google.com/issues/254292974)
 *
 * ### When it appears
 *
 * At least in the case that was investigated, the pattern that was causing
 * a bug was like this:
 *
 * - There's a parent class `A` with a non-abstract, but open composable method
 *   named `content` (the actual names don't matter). It's enough for this
 *   method to contain nothing but access some
 *   [State][androidx.compose.runtime.State] that would trigger
 *   its recomposition.
 * - There's a subclass `B`, which extends class `A`, and overrides its
 *   `content` method. It's enough that it just calls `super.content()` for
 *   the bug to appear.
 * - Triggering recomposition of `A.content` (e.g. by changing
 *   a [State][androidx.compose.runtime.State]'s value that is read in its
 *   implementation) causes the exception mentioned above to appear.
 *
 *   See an example [here](https://issuetracker.google.com/issues/329477544).
 *
 * ### The workaround
 *
 * It was found that for such a configuration it is enough to wrap
 * the implementation of composable method with another composable method that
 * doesn't introduce anything by itself except such wrapping.
 *
 * ### How to use this method
 *
 * If you encounter the bug mentioned above:
 *
 * - Find such a method that is being recomposed when the exception appears,
 *   which triggers the exception.
 *   - It is expected that it should be an open composable method in some parent
 *     class, and this method is overridden in a class (component) that is being
 *     used when the exception appears.
 *   - It's likely NOT a method that is either private, or final, or not
 *     `open`/`override` one, or the one that's not overridden in
 *     any subclasses.
 *   - The [Component.content] method's implementation in some of the parent
 *     component classes might be the one of typical cases.
 *
 * - Wrap the content of that method with this function, e.g. like this:
 *   ```kotlin
 *       override fun content(): Unit = recompositionWorkaround {
 *           // original method's content
 *       }
 *   ```
 *
 * - Please do not wrap any methods just in case, without confirming that it is
 *   exactly the one that triggers the problem.
 *
 * - If you find that this function should be applied in some other cases than
 *   described here, please update these instructions to be up-to-date.
 *
 * @param content
 *         the composable content in a parent composable method, whose
 *         recomposition was found to trigger the bug.
 * @return the wrapped composable content intended to fix the bug.
 */
@Composable
public fun recompositionWorkaround(content: @Composable () -> Unit) {
    content()
}

/**
 * A variant of [recompositionWorkaround], which can be usd on
 * [ReadOnlyComposable] methods.
 */
@Composable
@ReadOnlyComposable
public fun recompositionWorkaroundReadonly(content: ReadOnlyComposableCallback) {
    content.run()
}

/**
 * A functional interface, which can be used for declaring parameters that
 * accept the lambda, which is both [Composable] and [ReadOnlyComposable].
 */
public fun interface ReadOnlyComposableCallback {

    /**
     * Runs the callback.
     */
    @Composable
    @ReadOnlyComposable
    public fun run()
}

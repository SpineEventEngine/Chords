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

package io.spine.chords.proto.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.google.protobuf.Message
import io.spine.chords.core.AbstractComponentCompanion
import io.spine.chords.core.ComponentProps
import io.spine.chords.runtime.MessageField
import io.spine.protobuf.ValidatingBuilder

/**
 * A base class for creating companion objects for [MessageForm] and its
 * subclasses.
 *
 * This class introduces only a set of protected methods, which may or may not
 * be made public or used explicitly in a subclasses, depending on the needs of
 * actual implementation.
 */
public open class MessageFormCompanionBase<M: Message, F: MessageForm<M>>(
    createInstance: () -> F
) : AbstractComponentCompanion({ createInstance() }) {

    /**
     * Declares a `MessageForm` instance, which is not bound to a parent
     * form automatically, and can be bound to the respective data field
     * manually as needed.
     *
     * The form's content that is specified with the [content] parameter is
     * expected to include field editors for all message's fields, which
     * are required to create a valid value of type [M].
     *
     * @param B A type of the message builder.
     *
     * @param value The message value to be edited within the form.
     * @param builder A lambda that should create and return a new builder for
     *   a message of type [M].
     * @param props A lambda that can set any additional props on the form.
     * @param onBeforeBuild A lambda that allows to amend the message
     *   after any valid field is entered to it.
     * @param content A form's content, which can contain an arbitrary
     *   layout along with field editor declarations.
     * @return A form's instance that has been created for this
     *   declaration site.
     */
    @Composable
    protected fun <B : ValidatingBuilder<out M>> declareInstance(
        value: MutableState<M?>,
        builder: () -> B,
        props: ComponentProps<F> = ComponentProps {},
        onBeforeBuild: (B) -> Unit = {},
        content: @Composable FormPartScope<M>.() -> Unit
    ): F = declareMultipartInstance(value, builder, props, onBeforeBuild) {
        FormPart(content)
    }

    /**
     * Declares a `MessageForm` instance, which is automatically bound to
     * edit a parent form's field identified by [field].
     *
     * The form's content that is specified with the [content] parameter is
     * expected to include field editors for all message's fields, which
     * are required to create a valid value of type [M].
     *
     * @receiver The context introduced by the parent form.
     * @param PM Parent message type.
     * @param B A type of the message builder.
     *
     * @param field The parent message's field whose message is to be edited
     *   within this form.
     * @param builder A lambda that should create and return a new builder
     *   for a message of type [M].
     * @param props A lambda that can set any additional props on the form.
     * @param defaultValue A value that should be displayed in the form
     *   by default.
     * @param onBeforeBuild A lambda that allows to amend the message
     *   after any valid field is entered to it.
     * @param content A form's content, which can contain an arbitrary
     *   layout along with field editor declarations.
     * @return A form's instance that has been created for this
     *   declaration site.
     */
    context(FormFieldsScope<PM>)
    @Composable
    protected open fun <
            PM : Message,
            B : ValidatingBuilder<out M>
            >
            declareInstance(
        field: MessageField<PM, M>,
        builder: () -> B,
        props: ComponentProps<F> = ComponentProps {},
        defaultValue: M? = null,
        onBeforeBuild: (B) -> Unit = {},
        content: @Composable FormPartScope<M>.() -> Unit
    ): F = declareMultipartInstance(field, builder, props, defaultValue, onBeforeBuild) {
        FormPart(content)
    }

    /**
     * Declares a multipart `MessageForm` instance, which is not bound to
     * a parent form automatically, and can be bound to the respective data
     * field manually as needed.
     *
     * The form's content that is specified with the [content] parameter
     * should specify each form's part with using
     * [FormPart][MultipartFormScope.FormPart] declarations, which, in turn,
     * should contain the respective field editors.
     *
     * @param B A type of the message builder.
     *
     * @param value The message value to be edited within the form.
     * @param builder A lambda that should create and return a new builder
     *   for a message of type [M].
     * @param props A lambda that can set any additional props on the form.
     * @param onBeforeBuild A lambda that allows to amend the message
     *   after any valid field is entered to it.
     * @param content A form's content, which can contain an arbitrary
     *   layout along with field editor declarations.
     * @return A form's instance that has been created for this
     *   declaration site.
     */
    @Composable
    protected fun <B : ValidatingBuilder<out M>> declareMultipartInstance(
        value: MutableState<M?>,
        builder: () -> B,
        props: ComponentProps<F> = ComponentProps {},
        onBeforeBuild: (B) -> Unit = {},
        content: @Composable MultipartFormScope<M>.() -> Unit
    ): F = createAndRender({
        this.value = value

        // Storing the builder as `ValidatingBuilder` internally.
        @Suppress("UNCHECKED_CAST")
        this.builder = builder as () -> ValidatingBuilder<M>
        @Suppress(
            // Storing `onBeforeBuild` using a more general
            // `ValidatingBuilder<out M>` type internally.
            "UNCHECKED_CAST"
        )
        this.onBeforeBuild = onBeforeBuild as (ValidatingBuilder<out M>) -> Unit
        multipartContent = content
        props.run { configure() }
    }) {
        Content()
    }

    /**
     * Declares a multipart `MessageForm` instance, which is automatically
     * bound to edit a message in a parent form's field identified
     * by [field].
     *
     * The form's content that is specified with the [content] parameter
     * should specify each form's part with using
     * [FormPart][MultipartFormScope.FormPart] declarations, which, in turn,
     * should contain the respective field editors.
     *
     * @receiver The context introduced by the parent form.
     * @param PM Parent message type.
     * @param B A type of the message builder.
     *
     * @param field The parent message's field whose message is to be edited
     *   within this form.
     * @param builder A lambda that should create and return a new builder
     *   for a message of type [M].
     * @param props A lambda that can set any additional props on the form.
     * @param defaultValue A value that should be displayed in the form by default.
     * @param onBeforeBuild A lambda that allows to amend the message
     *   after any valid field is entered to it.
     * @param content A form's content, which can contain an arbitrary
     *   layout along with field editor declarations.
     * @return a form's instance that has been created for this
     *         declaration site.
     */
    context(FormFieldsScope<PM>)
    @Composable
    protected fun <
            PM : Message,
            B : ValidatingBuilder<out M>
            >
            declareMultipartInstance(
        field: MessageField<PM, M>,
        builder: () -> B,
        props: ComponentProps<F> = ComponentProps {},
        defaultValue: M? = null,
        onBeforeBuild: (B) -> Unit = {},
        content: @Composable MultipartFormScope<M>.() -> Unit
    ): F = createAndRender({
        // Storing the builder as `ValidatingBuilder` internally.
        @Suppress("UNCHECKED_CAST")
        this.builder = builder as () -> ValidatingBuilder<M>
        @Suppress(
            // Storing `onBeforeBuild` using a more general
            // `ValidatingBuilder<out M>` type internally.
            "UNCHECKED_CAST"
        )
        this.onBeforeBuild = onBeforeBuild as (ValidatingBuilder<out M>) -> Unit
        multipartContent = content
        props.run { configure() }
    }) {
        ContentWithinField(field, defaultValue)
    }

    /**
     * Creates a [MessageForm] instance without rendering it in
     * the composable content with the same call.
     *
     * This method can be used to create a form's instance outside
     * a composable context, and render it separately. A form's instance
     * created in this way, has to be rendered  explicitly by calling either
     * its [Content][MessageForm.Content] method (for singlepart forms) or its
     * [MultipartContent][MessageForm.MultipartContent] method (for rendering a
     * multipart form) in a composable context where it needs to be displayed.
     *
     * NOTE: this method creates a new instance each time it is invoked.
     * When invoking it in context of a `@Composable` method, make sure to
     * take this fact into account (e.g. caching the instance with
     * [remember][androidx.compose.runtime.remember]). This method would
     * typically not need to be invoked from `@Composable` methods though,
     * and the regular `@Composable` declarations (using one of the [invoke]
     * functions) would need to be used in the majority of cases.
     *
     * @param B A type of the message builder.
     *
     * @param value The message value to be edited within the form.
     * @param builder A lambda that should create and return a new builder
     *   for a message of type [M].
     * @param onBeforeBuild A lambda that allows to amend the message
     *   after any valid field is entered to it.
     * @param props A lambda that can set any additional props on the form.
     * @return A form's instance that has been created for this
     *   declaration site.
     */
    protected open fun <B : ValidatingBuilder<out M>> createInstance(
        value: MutableState<M?>,
        builder: () -> B,
        onBeforeBuild: (B) -> Unit = {},
        props: ComponentProps<F> = ComponentProps {}
    ): F =
        super.create(null) {
            this.value = value

            // Storing the builder as `ValidatingBuilder` internally.
            @Suppress("UNCHECKED_CAST")
            this.builder = builder as () -> ValidatingBuilder<M>
            @Suppress("UNCHECKED_CAST")
            this.onBeforeBuild = onBeforeBuild as (ValidatingBuilder<out M>) -> Unit
            props.run { configure() }
        }
}

/**
 * A class, which should be used for a declaring companion object of any custom
 * [MessageForm] subclass [F], which is designed to edit some concrete message
 * type [M].
 *
 * Adding such a companion object to a custom form subclass is needed to ensure
 * that it can be declared or created in various usage scenarios, similarly
 * to how any other [Component][io.spine.chords.core.Component] implementation
 * can be declared. See the "Implementing custom form components" section in
 * the [MessageForm]'s documentation.
 */
public class MessageFormCompanion<M: Message, F: MessageForm<M>>(
    createInstance: () -> F
) : MessageFormCompanionBase<M, F>(createInstance) {

    /**
     * Declares a form instance, which edits a value stored in
     * the [value] `MutableState`.
     *
     * The form's content that is specified with the [content] parameter is
     * expected to include field editors for all message's fields, which
     * are required to create a valid value of type [M].
     *
     * @param B A type of the message builder.
     *
     * @param value The message value to be edited within the form.
     * @param builder A lambda that should create and return a new builder for
     *   a message of type [M].
     * @param props A lambda that can set any additional props on the form.
     * @param onBeforeBuild A lambda that allows to amend the message
     *   after any valid field is entered to it.
     * @param content A form's content, which can contain an arbitrary
     *   layout along with field editor declarations.
     * @return A form's instance that has been created for this
     *   declaration site.
     */
    @Composable
    public fun <B : ValidatingBuilder<out M>> invoke(
        value: MutableState<M?>,
        builder: () -> B,
        props: ComponentProps<F>,
        onBeforeBuild: (B) -> Unit,
        content: @Composable FormPartScope<M>.() -> Unit
    ): F = declareInstance(value, builder, props, onBeforeBuild, content)

    /**
     * Declares a form instance, which is automatically bound to edit a parent
     * form's field identified by [field].
     *
     * The form's content that is specified with the [content] parameter is
     * expected to include field editors for all message's fields, which
     * are required to create a valid value of type [M].
     *
     * @receiver The context introduced by the parent form.
     * @param PM Parent message type.
     * @param B A type of the message builder.
     *
     * @param field The parent message's field whose message is to be edited
     *   within this form.
     * @param builder A lambda that should create and return a new builder
     *   for a message of type [M].
     * @param props A lambda that can set any additional props on the form.
     * @param defaultValue A value that should be displayed in the form
     *   by default.
     * @param onBeforeBuild A lambda that allows to amend the message
     *   after any valid field is entered to it.
     * @param content A form's content, which can contain an arbitrary
     *   layout along with field editor declarations.
     * @return A form's instance that has been created for this
     *   declaration site.
     */
    context(FormFieldsScope<PM>)
    @Composable
    public fun <PM : Message, B : ValidatingBuilder<out M>> invoke(
        field: MessageField<PM, M>,
        builder: () -> B,
        props: ComponentProps<F>,
        defaultValue: M?,
        onBeforeBuild: (B) -> Unit,
        content: @Composable FormPartScope<M>.() -> Unit
    ): F = declareInstance(field, builder, props, defaultValue, onBeforeBuild, content)

    /**
     * Declares a multipart `MessageForm` instance, which edits a value stored
     * in the [value] `MutableState`.
     *
     * The form's content that is specified with the [content] parameter
     * should specify each form's part with using
     * [FormPart][MultipartFormScope.FormPart] declarations, which, in turn,
     * should contain the respective field editors.
     *
     * @param B A type of the message builder.
     *
     * @param value The message value to be edited within the form.
     * @param builder A lambda that should create and return a new builder
     *   for a message of type [M].
     * @param props A lambda that can set any additional props on the form.
     * @param onBeforeBuild A lambda that allows to amend the message
     *   after any valid field is entered to it.
     * @param content A form's content, which can contain an arbitrary
     *   layout along with field editor declarations.
     * @return A form's instance that has been created for this
     *   declaration site.
     */
    @Composable
    public fun <B : ValidatingBuilder<out M>> Multipart(
        value: MutableState<M?>,
        builder: () -> B,
        props: ComponentProps<F>,
        onBeforeBuild: (B) -> Unit,
        content: @Composable MultipartFormScope<M>.() -> Unit
    ): F = declareMultipartInstance(value, builder, props, onBeforeBuild, content)

    /**
     * Declares a multipart form instance, which is automatically bound to edit
     * a message in a parent form's field identified by [field].
     *
     * The form's content that is specified with the [content] parameter
     * should specify each form's part with using
     * [FormPart][MultipartFormScope.FormPart] declarations, which, in turn,
     * should contain the respective field editors.
     *
     * @receiver The context introduced by the parent form.
     * @param PM Parent message type.
     * @param B A type of the message builder.
     *
     * @param field The parent message's field whose message is to be edited
     *   within this form.
     * @param builder A lambda that should create and return a new builder
     *   for a message of type [M].
     * @param props A lambda that can set any additional props on the form.
     * @param defaultValue A value that should be displayed in the form by default.
     * @param onBeforeBuild A lambda that allows to amend the message
     *   after any valid field is entered to it.
     * @param content A form's content, which can contain an arbitrary
     *   layout along with field editor declarations.
     * @return a form's instance that has been created for this
     *         declaration site.
     */
    context(FormFieldsScope<PM>)
    @Composable
    public fun <PM : Message, B : ValidatingBuilder<out M>> Multipart(
        field: MessageField<PM, M>,
        builder: () -> B,
        props: ComponentProps<F>,
        defaultValue: M?,
        onBeforeBuild: (B) -> Unit,
        content: @Composable MultipartFormScope<M>.() -> Unit
    ): F = declareMultipartInstance(field, builder, props, defaultValue, onBeforeBuild, content)

    /**
     * Creates a form instance without rendering it in the composable content
     * right away.
     *
     * This method can be used to create a form's instance outside
     * a composable context, and render it separately. A form's instance
     * created in this way, has to be rendered  explicitly by calling either
     * its [Content][MessageForm.Content] method (for singlepart forms) or its
     * [MultipartContent][MessageForm.MultipartContent] method (for rendering a
     * multipart form) in a composable context where it needs to be displayed.
     *
     * NOTE: this method creates a new instance each time it is invoked.
     * When invoking it in context of a `@Composable` method, make sure to
     * take this fact into account (e.g. caching the instance with
     * [remember][androidx.compose.runtime.remember]). This method would
     * typically not need to be invoked from `@Composable` methods though,
     * and the regular `@Composable` declarations (using one of the [invoke]
     * functions) would need to be used in the majority of cases.
     *
     * @param B A type of the message builder.
     *
     * @param value The message value to be edited within the form.
     * @param builder A lambda that should create and return a new builder
     *   for a message of type [M].
     * @param onBeforeBuild A lambda that allows to amend the message
     *   after any valid field is entered to it.
     * @param props A lambda that can set any additional props on the form.
     * @return A form's instance that has been created for this
     *   declaration site.
     */
    public fun <B : ValidatingBuilder<out M>> create(
        value: MutableState<M?>,
        builder: () -> B,
        onBeforeBuild: (B) -> Unit,
        props: ComponentProps<F>
    ): F = super.createInstance(value, builder, onBeforeBuild, props)
}

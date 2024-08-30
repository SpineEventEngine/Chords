/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * Dependencies on Spine modules.
 */
@Suppress("unused", "ConstPropertyName")
object Spine {

    const val group = "io.spine"
    const val toolsGroup = "io.spine.tools"

    /**
     * Versions for published Spine SDK artifacts.
     */
    object ArtifactVersion {

        /**
         * The version of [Spine.base].
         *
         * @see <a href="https://github.com/SpineEventEngine/base">spine-base</a>
         */
        const val base = "2.0.0-SNAPSHOT.203"

        /**
         * The version of [Spine.base_1_9]
         *
         * @see <a href="https://github.com/SpineEventEngine/base">spine-base</a>
         */
        const val base_1_9 = "1.9.0"

        /**
         * The version of [Spine.reflect].
         *
         * @see <a href="https://github.com/SpineEventEngine/reflect">spine-reflect</a>
         */
        const val reflect = "2.0.0-SNAPSHOT.187"

        /**
         * The version of [Spine.Logging].
         *
         * @see <a href="https://github.com/SpineEventEngine/logging">spine-logging</a>
         */
        const val logging = "2.0.0-SNAPSHOT.233"

        /**
         * The version of [Spine.testlib].
         *
         * @see <a href="https://github.com/SpineEventEngine/testlib">spine-testlib</a>
         */
        const val testlib = "2.0.0-SNAPSHOT.184"

        /**
         * The version of `core-java`.
         *
         * @see [Spine.CoreJava.client]
         * @see [Spine.CoreJava.server]
         * @see <a href="https://github.com/SpineEventEngine/core-java">core-java</a>
         */
        const val core = "2.0.0-SNAPSHOT.176"

        /**
         * The version of [Spine.modelCompiler].
         *
         * @see <a href="https://github.com/SpineEventEngine/model-compiler">spine-model-compiler</a>
         */
        const val mc = "2.0.0-SNAPSHOT.133"

        /**
         * The version of [McJava].
         *
         * @see <a href="https://github.com/SpineEventEngine/mc-java">spine-mc-java</a>
         */
        const val mcJava = "2.0.0-SNAPSHOT.206"

        /**
         * The version of [Spine.baseTypes].
         *
         * @see <a href="https://github.com/SpineEventEngine/base-types">spine-base-types</a>
         */
        const val baseTypes = "2.0.0-SNAPSHOT.126"

        /**
         * The version of [Spine.time].
         *
         * @see <a href="https://github.com/SpineEventEngine/time">spine-time</a>
         */
        const val time = "2.0.0-SNAPSHOT.135"

        /**
         * The version of [Spine.change].
         *
         * @see <a href="https://github.com/SpineEventEngine/change">spine-change</a>
         */
        const val change = "2.0.0-SNAPSHOT.118"

        /**
         * The version of [Spine.text].
         *
         * @see <a href="https://github.com/SpineEventEngine/text">spine-text</a>
         */
        const val text = "2.0.0-SNAPSHOT.6"

        /**
         * The version of [Spine.toolBase].
         *
         * @see <a href="https://github.com/SpineEventEngine/tool-base">spine-tool-base</a>
         */
        const val toolBase = "2.0.0-SNAPSHOT.217"

        /**
         * The version of [Spine.javadocTools].
         *
         * @see <a href="https://github.com/SpineEventEngine/doc-tools">spine-javadoc-tools</a>
         */
        const val javadocTools = "2.0.0-SNAPSHOT.75"

        /**
         * The version of [Spine.money]
         *
         * @see <a href="https://github.com/SpineEventEngine/money">spine-money</a>
         */
        const val money = "1.5.0"

        /**
         * The version of [Spine.bootstrap]
         *
         * @see <a href="https://github.com/SpineEventEngine/bootstrap">spine-bootstrap</a>
         *
         */
        const val bootstrap = "1.9.0"
    }

    const val base = "$group:spine-base:${ArtifactVersion.base}"

    /**
     * The dependency on Spine 1.9 to allow access to
     * `io.spine.protobuf.ValidatingBuilder` from the generated Kotlin code.
     */
    const val base_1_9 = "$group:spine-base:${ArtifactVersion.base_1_9}"

    const val reflect = "$group:spine-reflect:${ArtifactVersion.reflect}"
    const val baseTypes = "$group:spine-base-types:${ArtifactVersion.baseTypes}"
    const val time = "$group:spine-time:${ArtifactVersion.time}"
    const val change = "$group:spine-change:${ArtifactVersion.change}"
    const val text = "$group:spine-text:${ArtifactVersion.text}"
    const val money = "$group:spine-money:${ArtifactVersion.money}"

    const val testlib = "$toolsGroup:spine-testlib:${ArtifactVersion.testlib}"
    const val testUtilTime = "$toolsGroup:spine-testutil-time:${ArtifactVersion.time}"
    const val psiJava = "$toolsGroup:spine-psi-java:${ArtifactVersion.toolBase}"
    const val psiJavaBundle = "$toolsGroup:spine-psi-java-bundle:${ArtifactVersion.toolBase}"
    const val toolBase = "$toolsGroup:spine-tool-base:${ArtifactVersion.toolBase}"
    const val pluginBase = "$toolsGroup:spine-plugin-base:${ArtifactVersion.toolBase}"
    const val pluginTestlib = "$toolsGroup:spine-plugin-testlib:${ArtifactVersion.toolBase}"
    const val modelCompiler = "$toolsGroup:spine-model-compiler:${ArtifactVersion.mc}"
    const val bootstrap = "$toolsGroup:spine-bootstrap:${ArtifactVersion.bootstrap}"

    /**
     * Dependencies on the artifacts of the Spine Logging library.
     *
     * @see <a href="https://github.com/SpineEventEngine/logging">spine-logging</a>
     */
    object Logging {
        const val version = ArtifactVersion.logging
        const val lib = "$group:spine-logging:$version"

        const val log4j2Backend = "$group:spine-logging-log4j2-backend:$version"
        const val stdContext = "$group:spine-logging-std-context:$version"
        const val grpcContext = "$group:spine-logging-grpc-context:$version"
        const val smokeTest = "$group:spine-logging-smoke-test:$version"

        // Transitive dependencies.
        // Make `public` and use them to force a version in a particular repository, if needed.
        internal const val julBackend = "$group:spine-logging-jul-backend:$version"
        internal const val middleware = "$group:spine-logging-middleware:$version"
        internal const val platformGenerator = "$group:spine-logging-platform-generator:$version"
        internal const val jvmDefaultPlatform = "$group:spine-logging-jvm-default-platform:$version"

        @Deprecated(
            message = "Please use `Logging.lib` instead.",
            replaceWith = ReplaceWith("lib")
        )
        const val floggerApi = "$group:spine-flogger-api:$version"

        @Deprecated(
            message = "Please use `grpcContext` instead.",
            replaceWith = ReplaceWith("grpcContext")
        )
        const val floggerGrpcContext = "$group:spine-flogger-grpc-context:$version"
    }

    /**
     * Dependencies on Spine Model Compiler for Java.
     *
     * See [mc-java](https://github.com/SpineEventEngine/mc-java).
     */
    @Suppress("MemberVisibilityCanBePrivate") // `pluginLib()` is used by subprojects.
    object McJava {
        const val version = ArtifactVersion.mcJava
        const val pluginId = "io.spine.mc-java"
        val pluginLib = pluginLib(version)
        fun pluginLib(version: String): String = "$toolsGroup:spine-mc-java-plugins:$version:all"
    }

    @Deprecated("Please use `javadocFilter` instead.", ReplaceWith("javadocFilter"))
    const val javadocTools = "$toolsGroup::${ArtifactVersion.javadocTools}"
    const val javadocFilter = "$toolsGroup:spine-javadoc-filter:${ArtifactVersion.javadocTools}"

    const val client = CoreJava.client // Added for brevity.
    const val server = CoreJava.server // Added for brevity.

    /**
     * Dependencies on `core-java` modules.
     *
     * See [`SpineEventEngine/core-java`](https://github.com/SpineEventEngine/core-java/).
     */
    object CoreJava {
        const val version = ArtifactVersion.core
        const val core = "$group:spine-core:$version"
        const val client = "$group:spine-client:$version"
        const val server = "$group:spine-server:$version"
        const val server_1_9 = "$group:spine-server:${ArtifactVersion.base_1_9}"
        const val testUtilServer = "$toolsGroup:spine-testutil-server:$version"
    }
}

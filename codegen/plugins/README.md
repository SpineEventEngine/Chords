# `codegen-plugins`

A separate Gradle project with Spine Compiler plugins that generate
[MessageField](../runtime/src/main/kotlin/io/spine/chords/runtime/MessageField.kt),
[MessageOneof](../runtime/src/main/kotlin/io/spine/chords/runtime/MessageOneof.kt),
and [MessageDef](../runtime/src/main/kotlin/io/spine/chords/runtime/MessageDef.kt)
implementations for Proto messages. Also, some other useful Kotlin extensions are generated, 
e.g. `ValidatingBuilder.messageDef()` that returns the instance of `MessageDef` implementation
for the current message builder.

The root consumes the generator through a composite build. Both builds use the same toolchain;
standalone generator builds also resolve the runtime from this checkout.

### Requirements

- Java 25
- Gradle `9.8.2`
- Kotlin `2.4.21`
- Spine Compiler `2.0.0-SNAPSHOT.083`

### How to use

Apply `io.spine.core-jvm`, then add the generator to the Compiler classpath:

```kotlin
dependencies {
    add("spineCompiler", "io.spine.chords:spine-chords-codegen-plugins:$chordsVersion")
}

spine {
    compiler {
        plugins("io.spine.chords.codegen.plugins.MessageFieldsPlugin")
    }
}
```

Chords definitions and Spine validation code are generated in the same Compiler invocation.
For external consumers, publish the runtime and generator with
`publishCodegenPluginsToMavenLocal`.

### Modules

* [codegen-plugins](codegen-plugins) — the Spine Compiler plugins generating the code.

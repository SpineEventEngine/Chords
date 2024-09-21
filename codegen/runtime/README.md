# Spine Chords Codegen Runtime

A library that introduces an extended Protobuf messages API, which is made
available thanks to [Chords code generation facilities](../plugins/README.md).

## Using Spine Chords Codegen Runtime in a Gradle project

Add a dependency to the library as follows:
```kotlin
dependencies {
    implementation("io.spine.chords:spine-chords-codegen-runtime:$chordsVersion")
}
```

## Overview of library's facilities

This library introduces such types as listed below, which can conceptually be
thought of as more performance-effective analogs of respective Protobuf
descriptors that also introduce a more convenient Kotlin API
whenever appropriate.

- [MessageDef](src/main/kotlin/io/spine/chords/runtime/MessageDef.kt)
- [MessageField](src/main/kotlin/io/spine/chords/runtime/MessageField.kt)
- [MessageOenof](src/main/kotlin/io/spine/chords/runtime/MessageOneof.kt)

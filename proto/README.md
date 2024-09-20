# Spine Chords Proto

Domain-specific UI components that operate with models defined 
as Protobuf messages. 

## Using Spine Chords Proto in a Gradle project

Add a dependency to the library as follows:
```kotlin
dependencies {
    implementation("io.spine.chords:spine-chords-proto:$chordsVersion")
}
```

Besides, make sure to add all dependencies for the following libraries:
- [Spine Chords Core](../core/README.md)
- [Spine Proto Values](../proto-values/README.md)
- [Spine Codegen Runtime](../codegen/runtime/README.md)

## Overview of library's facilities

This library uses message type declarations defined in the `spine-base` and
the [Spine Proto Values](../proto-values) library. It includes such categories 
of components:
- A generic editor for multi-field Protobuf `Message` values, which basically 
  represents an input form. See the documentation for the 
  [MessageForm](src/main/kotlin/io/spine/chords/proto/form/MessageForm.kt)
  component for details.
- Components working with monetary values — see 
  the [`money`](src/main/kotlin/io/spine/chords/proto/money) package.
- Components working with networking related values — see
  the [`net`](src/main/kotlin/io/spine/chords/proto/net) package.
- Components working with time related values — see
  the [`time`](src/main/kotlin/io/spine/chords/proto/time) package.
- etc.

# Spine Chords Proto Values

Introduces some Protobuf message types that complement the ones available in
the `spine-base` library, as well as Kotlin extensions for Protobuf messages
declared in `spine-base` library.

## Using Spine Chords Proto Values in a Gradle project

Add a dependency to the library as follows:
```kotlin
dependencies {
    implementation("io.spine.chords:spine-chords-proto-values:$chordsVersion")
}
```

## Overview of library's facilities

The library introduces Protobuf messages that represent such concepts as
[IP addresses](src/main/proto/spine/chords/proto/value/net/ip_address.proto),
[payment card numbers and bank accounts](src/main/proto/spine/chords/proto/value/money/payments.proto),
as well as some Kotlin extensions for these and other existing types like
[`Money`](src/main/kotlin/io/spine/chords/proto/value/money/MoneyExts.kt),
[`Timestamp`](src/main/kotlin/io/spine/chords/proto/value/time/TimeExts.kt),
[`PersonName`](src/main/kotlin/io/spine/chords/proto/value/person/PersonNameExts.kt),
etc.

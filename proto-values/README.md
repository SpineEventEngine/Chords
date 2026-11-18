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

## Spine 2 compatibility

Spine Money 2 represents `Currency` as a message containing a code and stores fractional
amounts in attos (10^-18). Its wire representation differs from Money 1; stored or transmitted
Money 1 values require an explicit data migration. Use `Currencies.USD` or
`Currencies.of("USD")` instead of enum constants. Currency information is available from
`Currency.info()`; the Chords `options` extension now returns `CurrencyInfo`.

Spine validation builders now implement `io.spine.validation.ValidatingBuilder`.
`build()` validates the message; use `buildPartial()` for deliberately incomplete values.

Required oneof groups use `(choice).required = true`. The replaced `(is_required)` option
still parses in Spine 2 but does not enable validation. Consumers generating Chords metadata
must migrate their declarations to `choice`; message field tags and wire types are unchanged.

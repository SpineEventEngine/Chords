## Chords Protobuf
Domain-specific UI components that operate with models defined 
as Protobuf messages. 

This library uses message type declarations defined in the `spine-base` and
the [proto-values](../proto-values) library. It includes such categories 
of components:
- A generic editor for multi-field Protobuf `Message` values, which basically 
  represents an input form. See the 
  [MessageForm](src/main/kotlin/io/spine/chords/form/MessageForm.kt)
  component.
- Components working with monetary values — see 
  the [`money`](src/main/kotlin/io/spine/chords/money) package.
- Components working with networking related values — see
  the [`net`](src/main/kotlin/io/spine/chords/net) package.
- Components working with time related values — see
  the [`time`](src/main/kotlin/io/spine/chords/time) package.
- etc.

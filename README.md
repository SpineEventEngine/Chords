# Chords
A suite of libraries for UI development with Compose Multiplatform toolkit

> Note: the libraries are currently on an experimental stage.

This suite contains the following libraries:
- [core](core/README.md) — a basis for writing desktop applications with Compose. 
- [proto](proto/README.md) — domain-specific UI components that operate with models defined
  as Protobuf messages.
- [proto-values](proto-values/README.md) — Protobuf messages and extensions that complement the ones
  found in standard Spine libraries, and are required by the `protobuf` library.
- [client](client/README.md) — components that support server connectivity using
  the [Spine Event Engine](https://spine.io/) framework.
- [codegen-runtime](codegen/runtime) — runtime API onto which the generated code relies.
- [codegen-plugins](codegen/plugins) — a separate Gradle project that generates 
    [MessageField](codegen/runtime/src/main/kotlin/io/spine/chords/runtime/MessageField.kt),
    [MessageOneof](codegen/runtime/src/main/kotlin/io/spine/chords/runtime/MessageOneof.kt),
    and [MessageDef](codegen/runtime/src/main/kotlin/io/spine/chords/runtime/MessageDef.kt)
    implementations for the fields of Proto messages. 
    See [README.md](codegen/plugins/README.md) for detail.
- [codegen-workspace](codegen/workspace) — Git submodule that refers
    [codegen_workspace](https://github.com/SpineEventEngine/Chords/tree/codegen_workspace) branch
    of the [Chords](https://github.com/SpineEventEngine/Chords) repo
    and contains `codegen-workspace` Gradle project that is a working-directory module 
    for code generation; it is used as a container for the Proto source code, 
    for which the codegen is to be performed.
- [codegen-tests](codegen/tests) — tests that checks the code generation.

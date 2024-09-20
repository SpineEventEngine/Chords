# Chords
A suite of libraries for desktop UI development with Compose Multiplatform toolkit

> Note: the libraries are currently on an experimental stage 

Please see the documentation for respective libraries and the details on their contents and usage:
- [Chords Core](core/README.md) — a library that provides a basis for writing desktop applications
  with Compose by introducing high-level support for creating Compose applications, a notion of
  class-based components, as well as some basic components.
- [Chords Proto](proto/README.md) — a library that works on top of Chords Core and provides 
  domain-specific UI components that operate with models defined as Protobuf messages.
- [Chords Proto Values](proto-values/README.md) — Protobuf messages and respective Kotlin extensions
  that complement the ones found in standard Spine libraries, and are required by
  the Chords Proto library.
- [Chords Client](client/README.md) — a library that works on top of Chords Proto and provides
  facilities and components that support server connectivity using
  the [Spine Event Engine](https://spine.io/) framework.

# Supported environment

The libraries have been tested to work in the following environment:
- JDK version 11
- Kotlin version 1.8.20
- Compose Multiplatform version 1.5.12
- Spine Event Engine version 1.9.0
- Gradle version 6.9.4
- [codegen-runtime](codegen/runtime) — runtime API onto which the generated code relies.
- [codegen-plugins](codegen/plugins) — separate Gradle project with ProtoData plugins
  that generate Kotlin extensions for Proto messages. 
  See [codegen/plugins/README.md](codegen/plugins/README.md) for detail.
- [codegen-workspace](codegen/workspace) — separate Gradle project that is 
  a working-directory module where the [codegen-plugins](codegen/plugins) are to be applied; 
  it is used as a container for the Proto source code, for which the codegen is to be performed.
  See [codegen/workspace/README.md](codegen/workspace/README.md) for details.
- [codegen-tests](codegen/tests) — tests that check the correctness of code generation.

# `codegen-plugins`

A separate Gradle project with ProtoData plugins which generate
[MessageField](codegen/runtime/src/main/kotlin/io/spine/chords/runtime/MessageField.kt),
[MessageOneof](codegen/runtime/src/main/kotlin/io/spine/chords/runtime/MessageOneof.kt),
and [MessageDef](codegen/runtime/src/main/kotlin/io/spine/chords/runtime/MessageDef.kt)
implementations for Proto messages. Also, some other useful Kotlin extensions are generated, 
e.g. `ValidatingBuilder.messageDef()` that returns the instance of `MessageDef` implementation
for the current message builder.

The separate Gradle project is needed because the ProtoData plugins, 
which generate the code, require the newer version of Gradle, `7.6.x` at the moment,
comparing to Chords-based projects, which require Gradle version `6.9.x`.

### Requirements

- Java 11
- Gradle `7.6.x`

### How to use

The [Chords Gradle plugin](https://github.com/SpineEventEngine/Chords-Gradle-plugin) 
should be used to apply `codegen-plugins` to a project.

### Resources for the Gradle plugin

The following files are copied to the
[resources/codegen-workspace](codegen-plugins/src/main/resources/codegen-workspace)
folder during the build:
* `buildSrc` folder.
* Gradle wrapper files.

These resources will be packaged into the resulting jar and then used by 
the Gradle plugin to create a placeholder module for code generation.

This is necessary for the following reasons:
* The Gradle plugin may not keep another copy of these files.
* There is no need to synchronize dependencies between `codegen-plugins` project
and the Gradle plugin.

### Modules

* [codegen-plugins](codegen-plugins) — the ProtoData plugins generating the code.

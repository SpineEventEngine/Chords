# `codegen-workspace`

Gradle project that is a working-directory module where the 
[spine-chords-codegen-plugins](https://github.com/SpineEventEngine/Chords/tree/master/codegen/plugins) 
are to be applied.

### Requirements

- Java 11
- Gradle `7.6.x`

### How to use

It should be added as a Git submodule to the main project repo if the code generation is required
for Proto sources of some module.

See [spine-chords-codegen-plugins/README.md](https://github.com/SpineEventEngine/Chords/tree/master/codegen/plugins/README.md)
on how to configure and run the code generation.

### Modules

- [workspace](workspace) — working-directory module that is used as a container 
  for te Proto source code, for which the codegen is to be performed.

### How it works

1. Before executing `generateProto` task, the Proto source code
   of the configured module is copied to the `workspace/src/main/proto` folder.

2. Once `launchProtoData` task is executed, the generated Kotlin sources
   are copied back to the `generatedSources/src/main/kotlin` folder of the original module.

The same logic applies to `test` sources.

See [workspace/build.gradle.kts](workspace/build.gradle.kts) for detail.

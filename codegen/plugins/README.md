# `codegen-plugins`

Gradle project that generates
[MessageField](codegen/runtime/src/main/kotlin/io/spine/chords/runtime/MessageField.kt),
[MessageOneof](codegen/runtime/src/main/kotlin/io/spine/chords/runtime/MessageOneof.kt),
and [MessageDef](codegen/runtime/src/main/kotlin/io/spine/chords/runtime/MessageDef.kt)
implementations for the fields of Proto messages.

The separate Gradle project is needed because the ProtoData plugins, 
that generate the code, require the newer version of Gradle 
comparing to 1DAM and Chords projects.

So the plugins require Spine version `2.0.x` with Gradle version `7.6.x` and 
therefore cannot be applied to the projects on Spine `1.9.x` with Gradle `6.9.x` directly.

### How to us

The code generation is performed by running the `codegen/workspace` Gradle project. 
See [codegen-workspace](./../workspace/README.md) for detail.

The following steps of configuration should be completed in order 
to run the code generation:

* Add Git submodule that refers
  [codegen_workspace](https://github.com/SpineEventEngine/Chords/tree/codegen_workspace) 
  branch of [Chords](https://github.com/SpineEventEngine/Chords) repository 
  to the `codegen/workspace` folder of the main project repository.

* Copy [RunCodegenPlugins](buildSrc/src/main/kotlin/io/spine/internal/gradle/RunCodegenPlugins.kt) 
Gradle task to your project.

* Configure and execute this task in the module, which requires the code generation,
before `compileKotlin` in the way like following:

```kotlin
val runCodegenPlugins = tasks.register<RunCodegenPlugins>("runCodegenPlugins") {

    // Dependencies that are required to load the Proto files from.
    dependencies(
        // Below is an declaration example.
        //
        // This is a list of the external dependencies,
        // onto which the processed Proto sources depend.
        //
        Spine.Money.lib,
        Projects.Users.lib,
        OneDam.DesktopAuth.lib,
        OneDam.ChordsProtoExt.lib
    )

    // Publish the local Gradle modules, onto which the original 
    // Proto source code depends, to `mavenLocal`.
    dependsOn(
        // These ones are just an example. In scope of this example,
        // several local Gradle modules are published to `mavenLocal`,
        // so that the codegen process is able to access them.
        //
        // In your use-case, the list of local modules will differ.
        //
        project(":desktop-auth")
            .tasks.named("publishToMavenLocal"),
        project(":chords-proto-ext")
            .tasks.named("publishToMavenLocal")
    )
}

// Run the code generation before `compileKotlin` task.
tasks.named("compileKotlin") {
    dependsOn(
        runCodegenPlugins
    )
}
```

If the `RunCodegenPlugins` task fails, this also causes the main build to fail.

### Modules

* [codegen-plugins](codegen-plugins) — the ProtoData plugin generating the code.

### How it works

Before executing the `generateProto` task, the Proto source code 
of the specified module is copied to the `codegen/workspace/src/main/proto` folder.
Once `launchProtoData` task is executed, the generated Kotlin sources
are copied back to the `generatedSources` folder of the source module.

The same logic applies to `test` sources.

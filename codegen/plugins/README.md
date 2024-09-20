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
comparing to 1DAM and Chords projects, which require Gradle version `6.9.x`.

### Requirements

- Java 11
- Gradle `7.6.x`

### How to us

The following steps of configuration should be completed in order 
to run the code generation:

* Add Git submodule that refers
  [codegen_workspace](https://github.com/SpineEventEngine/Chords/tree/codegen_workspace) 
  branch of Chords repository 
  to the `codegen/workspace` folder of the main project repository.

* Copy [RunCodegenPlugins](buildSrc/src/main/kotlin/io/spine/internal/gradle/RunCodegenPlugins.kt) 
Gradle task to your project.

* Configure and execute this task in the module, which requires the code generation,
before `compileKotlin` Gradle task in the way like following:

```kotlin
val runCodegenPlugins = tasks.register<RunCodegenPlugins>("runCodegenPlugins") {

    // The version of `spine-chords-codegen-plugins` to be used for code generation.
    //
    // Do not set this property for modules in Chords project — 
    // the current project version is used by default.
    //
    pluginsVersion = "2.0.0-SNAPSHOT.17"
    
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
  
    // ==============================================================================
    //
    // The properties below are optional and should not be configured in common case.
    //
    // Set it only if the default values are unsuitable for some reason.
    //
    // ==============================================================================

    // Path to the `codegen-workspace` module where the code generation is actually performed.
    //
    // The default value is `${project.rootDir}/codegen/workspace`.
    //
    // In your use-case, the value will differ.
    //
    workspaceDir = "${project.rootDir}/codegen-workspace"
  
    // Path to the module to generate the code for.
    //
    // The default value is `project.projectDir.path`.
    //
    // In your use-case, the value will differ.
    //
    sourceModuleDir = "${project.rootDir}/model"
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

* [codegen-plugins](codegen-plugins) — the ProtoData plugins generating the code.

### How it works

Actually, the code generation is performed by building the 
[codegen-workspace](https://github.com/SpineEventEngine/Chords/tree/codegen_workspace) 
Gradle project, into which the configured Proto source code is copied during the build.
See [codegen-workspace/README.md](https://github.com/SpineEventEngine/Chords/blob/codegen_workspace/README.md) 
for detail.

In short: before executing the `generateProto` task, the Proto source code 
of the specified module is copied to the `workspace/src/main/proto` folder; 
once `launchProtoData` task is executed, the generated Kotlin sources
are copied back to the `generatedSources/src/main/kotlin` folder of the original module.

The same logic applies to `test` sources.

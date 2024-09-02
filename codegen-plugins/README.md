## `codegen-plugins` — a separate Gradle project that generates `MessageField` and `MessageOneof` implementations for the fields of  Proto messages.

> The separate Gradle project is needed because the ProtoData plugin, 
that generates the code, requires the newer version of Gradle 
comparing to 1DAM and Chords projects.

### Run code generation

The following steps of configuration should be completed in order 
to run the code generation:

* Add Git submodule that refers [codegen_plugins](https://github.com/SpineEventEngine/Chords/tree/codegen_plugins) 
branch of [Chords](https://github.com/SpineEventEngine/Chords) repository 
to the main project repository.

* Copy [RunCodegenPlugins](buildSrc/src/main/kotlin/io/spine/internal/gradle/RunCodegenPlugins.kt) 
Gradle task to the main project.

* Configure and execute this task in the module, which requires the code generation,
before `compileKotlin` in the way like following:

```kotlin
val runCodegenPlugins = tasks.register<RunCodegenPlugins>("runCodegenPlugins") {
    // Path to the directory where the `codegen-plugins` project is located.
    pluginsDir = "${rootDir}/chords-codegen/codegen-plugins"
    // Path to the module which requires the code generation.
    sourceModuleDir = "${rootDir}/model"
    // Dependencies that are required to load the Proto files from.
    dependencies(
        Spine.Money.lib,
        Projects.Users.lib,
        OneDam.DesktopAuth.lib,
        OneDam.ChordsProtoExt.lib
    )
    task("build")

    // Publish to `mavenLocal` required dependencies.
    dependsOn(
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

> If the build of `codegen-plugins` project fails, this also
causes the main build to fail.

### Modules

* `message-fields` — contains implementation of the ProtoData plugin that 
generates the code.

* `model` — the placeholder where the Proto sources of the module, which requires 
code generation, will be copied during the build. The generated code 
will be copied back to the `generatedSources` folder of the source module.

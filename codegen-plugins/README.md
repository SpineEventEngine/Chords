# `codegen-plugins`

A Gradle project that generates `MessageField` and `MessageOneof` implementations for the fields of  Proto messages.

> The separate Gradle project is needed because the ProtoData plugin, 
that generates the code, requires the newer version of Gradle 
comparing to 1DAM and Chords projects.

### How to use

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
    // `chords-codegen` — the name of Git submodule. Set the proper value for your case.
    pluginsDir = "${rootDir}/chords-codegen/codegen-plugins"
    
    // Path to the module which requires the code generation.
    // `model` — the name of the module. Put the proper valur for your case.
    sourceModuleDir = "${rootDir}/model"
    
    // Dependencies that are required to load the Proto files from.
    dependencies(
        // These ones are just an example.
        // Pass the libraries that provides Proto files your code depended on.
        Spine.Money.lib,
        Projects.Users.lib,
        OneDam.DesktopAuth.lib,
        OneDam.ChordsProtoExt.lib
    )

    // Publish to `mavenLocal` required dependencies.
    dependsOn(
        // These ones are just an example.
        // Set the modules from your project that are specified as dependencies.
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
> causes the main build to fail.

### Modules

* `message-fields` — the ProtoData plugin generating the code.
* `model` — a working-directory module; it is used as a container for the Proto source code,
  for which the codegen is to be performed.

### How it works

Before executing the `generateProto` task, the Proto source code 
of the specified module is copied to the `model/src/main/proto` folder.
Once `launchProtoData` task is executed, the generated Kotlin sources
are copied back to the `generatedSources` folder of the source module.

The same logic applies to `test` sources.

> Please note that the `compileKotlin` and `compileTestKotlin` tasks are disabled
> in the `model` module due to dependency on `ValidatingBuilder` from Spine 1.9.x. 
> And therefore there are no tests to check the correctness of the generated files.

See the [model/build.gradle.kts](model/build.gradle.kts) for details.

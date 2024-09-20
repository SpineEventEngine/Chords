# Chords Client

This library extends the set of components and facilities provided by the 
Chords Core and Chords Proto libraries with components that support server
connectivity using the [Spine Event Engine](https://spine.io/) framework.

## Using Spine Chords Client in a Gradle project

Add a dependency to the library as follows:
```kotlin
dependencies {
    implementation("io.spine.chords:spine-chords-client:$chordsVersion")
}
```
Besides, make sure to add all dependencies for 
the [Spine Chords Proto](../core/README.md) library.

## Overview of library's facilities

This library includes such facilities and components as:
- [Client](src/main/kotlin/io/spine/chords/client/Client.kt) — an API for
  interacting with the application server, and the respective 
  [DesktopClient](src/main/kotlin/io/spine/chords/client/DesktopClient.kt)
  implementation.
- [ClientApplication](src/main/kotlin/io/spine/chords/client/appshell/ClientApplication.kt) —
  an extension of `Application` (introduced in the Chords Core module), which
  includes a server connection.
- [CommandMessageForm](src/main/kotlin/io/spine/chords/client/form/CommandMessageForm.kt) —
  a variant of the `MessageForm` component (introduced in the Chords Proto 
  module), which allows editing a posting a command message to the server.
- [CommandWizard](src/main/kotlin/io/spine/chords/client/form/CommandMessageForm.kt) —
  a variant of the `Wizard` component introduced in the Chords module, which
  represents a multipage editor for a command message, and allows posting the
  resulting command to the application server.
- etc.

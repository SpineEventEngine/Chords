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

### Extending Application Shell with server communication capabilities

One of the central pieces that this library adds is the 
[Client](src/main/kotlin/io/spine/chords/client/Client.kt) interface, which
provides ways of interacting with the application server, as well as the
respective [DesktopClient](src/main/kotlin/io/spine/chords/client/DesktopClient.kt)
implementation.

Since server communication can pervasively be needed throughout the
application's implementation, this library also introduces a respective
subclass of [Application](../core/src/main/kotlin/io/spine/chords/core/appshell/Application.kt),
called [ClientApplication](src/main/kotlin/io/spine/chords/client/appshell/ClientApplication.kt),
which includes a server connection. This way, a `Client` API is made accessible
via the [app.client](src/main/kotlin/io/spine/chords/client/appshell/ClientApplication.kt)
property, which is available globally.

### Server-aware components

This library also introduces some components that leverage 
the server connectivity:

- [CommandMessageForm](src/main/kotlin/io/spine/chords/client/form/CommandMessageForm.kt) —
  a variant of the `MessageForm` component (introduced in the Chords Proto 
  module), which allows creating custom per-field editors of command messages,
  and has built in means of posting the resulting command to the server.
 
- [CommandWizard](src/main/kotlin/io/spine/chords/client/form/CommandMessageForm.kt) —
  a variant of the `Wizard` component introduced in the Chords Core module,
  which represents a multipage editor for a command message, and allows posting
  the resulting command to the application server.

- etc.

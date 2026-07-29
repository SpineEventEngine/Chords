# Spine Chords Client

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

### Observing server data and connection status

The `readAndObserve()` and `readOneAndObserve()` functions return a
[`DataObservation`](src/main/kotlin/io/spine/chords/client/Client.kt). A data
observation is a Compose `State`, so it supports ordinary property delegation:

```kotlin
val projects by app.client.readAndObserve(
    Project::class.java,
    Project::getId
)
```

If the server connection is lost, an observation retains its last value. After
the connection is restored, it automatically re-reads its complete value and
creates a new subscription. Consumers can inspect `DataObservation.status`
when they need to distinguish active, waiting, refreshing, failed, and
cancelled observations.

The caller owns each observation's lifecycle. Call `DataObservation.cancel()`
when an observation is no longer needed so its server subscription and
automatic recovery registration can be released.

Failures that are not classified as temporary connection loss produce a
`DataObservationStatus.Failed` status. They are not retried during later
connection cycles. After resolving the cause, call `DataObservation.refresh()`
explicitly to retry the observation.

Applications can collect `Client.connectionStatus` to show connection-wide UI:

```kotlin
val connectionStatus by app.client.connectionStatus.collectAsState()
```

The status distinguishes connecting, connected, temporarily unavailable, and
closed clients. It lets applications notify users while ordinary data
observations recover automatically.

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

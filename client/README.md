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
[`DataObservation`](src/main/kotlin/io/spine/chords/client/DataObservation.kt).
A data observation is a Compose `State`, so it supports ordinary property delegation:

```kotlin
val projects by app.client.readAndObserve(
    Project::class.java,
    Project::getId
)
```

These functions do not wait for the server. They return an observation that
already holds its initial or default value — an empty list, `null`, or the
supplied default — while the initial read and subscription proceed in the
background. The value read from the server appears in the observation once they
complete, which recomposes the Compose code that reads it. Creating an
observation therefore never blocks the UI thread, even when the server is
unreachable.

A new observation carries the `DataObservationStatus.Refreshing` status while
its initial read is in progress. If the connection is already known to be
unavailable, the observation is instead returned in
`DataObservationStatus.WaitingForConnection` and is refreshed once the
connection is restored, without a request being spent on a channel that is
known to be down.

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

- [CommandDialog](src/main/kotlin/io/spine/chords/client/layout/CommandDialog.kt) —
  a modal form for constructing and posting a command message.

- [CommandWizard](src/main/kotlin/io/spine/chords/client/layout/CommandWizard.kt) —
  a variant of the `Wizard` component introduced in the Chords Core module,
  which represents a multipage editor for a command message, and allows posting
  the resulting command to the application server.

- etc.

#### Confirming cancellation of a command wizard

A `CommandWizard` reports through its `dirty` property whether any data has been
entered on any of its pages, with the same meaning that `CommandDialog.dirty`
has. All pages edit the fields of a single command message form, so `dirty`
stays `true` for the data entered on a page that is not displayed anymore.

The `Wizard.onBeforeCancel` callback is invoked when the user presses "Cancel",
and returning `false` from it keeps the wizard open. The callback suspends, so
a confirmation can be awaited inside it, and the wizard keeps the entered data
while the confirmation is pending:

```kotlin
val wizard = ImportItemWizard().apply {
    onCloseRequest = { wizardShown = false }
    onBeforeCancel = {
        !dirty || ConfirmationDialog.showConfirmation {
            message = "Discard the data entered so far?"
        }
    }
}
```

Successful submission closes the wizard through `close()`, which doesn't consult
`onBeforeCancel`, so the confirmation above is never displayed after the command
has been posted successfully. `onCloseRequest` is still invoked on both paths,
and remains the callback that removes the wizard from the composition.

#### Handling network errors in command modals

When posting from a `CommandDialog` or `CommandWizard` fails because of a
network communication error, Chords clears the posting state and keeps the
component open by default. The entered form data remains available, and the
user can retry manually after checking whether repeating the command is safe.
Chords does not retry commands automatically.

The default message asks the user to retry after the connection is restored.
Before retrying, the application should account for an uncertain operation
status: a missing acknowledgement does not prove that the server never
accepted the command.

Use `commandConsequencesProps` to customize one component:

```kotlin
commandConsequencesProps = Props {
    closeOnNetworkError = true
    networkErrorMessage = "Connection lost. Please check the operation status."
}
```

Setting `closeOnNetworkError` to `true` requests the previous close-on-error
behavior after the error presentation finishes. A `CommandWizard` closes only
when its host handles `onCloseRequest`.

Use shared defaults to customize all command dialogs and wizards:

```kotlin
override fun SharedDefaultsScope.sharedDefaults() {
    ModalCommandConsequences::class defaultsTo {
        networkErrorMessage = "The server is temporarily unavailable."
        networkErrorPresentation = { error ->
            showMessage(
                if (error.acknowledgementReceived) {
                    "Connection lost while waiting for the operation result."
                } else {
                    networkErrorMessage
                }
            )
        }
    }
}
```

The custom presentation receives `ModalCommandNetworkError`, which contains
the communication exception and whether Chords received an acknowledgement.
Chords still resets the posting state and applies `closeOnNetworkError`.

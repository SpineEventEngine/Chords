## Chords Client

Extends the set of components and facilities provided by the Chords Core
and Chords Proto libraries with components that support server connectivity using
the [Spine Event Engine](https://spine.io/) framework.

This involves such facilities and components as:
- [Client](src/main/kotlin/io/spine/chords/Client.kt) — an API for
  interacting with the application server, and the respective 
  [DesktopClient](src/main/kotlin/io/spine/chords/DesktopClient.kt)
  implementation.
- [ClientApplication](src/main/kotlin/io/spine/chords/appshell/ClientApplication.kt) —
  an extension of `Application` (introduced in the Chords module), which also has
  a server connection.
- [CommandMessageForm](src/main/kotlin/io/spine/chords/form/CommandMessageForm.kt) —
  a variant of the `MessageForm` component (introduced in the Chords Proto 
  module), which allows editing a posting a command message to the server.
- [CommandWizard](src/main/kotlin/io/spine/chords/form/CommandMessageForm.kt) —
  a variant of the `Wizard` component introduced in the Chords module, which
  represents a multipage editor for a command message, and allows posting the
  resulting command to the application server.
- etc.

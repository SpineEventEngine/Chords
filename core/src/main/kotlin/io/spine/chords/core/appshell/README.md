## `io.spine.chords.appshell` — Application Shell

This package introduces a generic basis that can be used to simplify the 
implementation of Compose desktop applications that follow a multiview
UI organization.

In order to create such an application you need to do the following:
- Implement each view as a subclass of [AppView](AppView.kt).
- Create an instance of the [Application](Application.kt) class (or subclass)
  with the respective `AppView` instances.
- Invoke the `run` method on this `Application` instance.

Once the application is run, its reference becomes available via the global
[app](Application.kt) property, which allows to get access to certain standard
application-wide APIs from within any component.

See the details in the [Application](Application.kt) and 
[AppView](AppView.kt) KDocs.

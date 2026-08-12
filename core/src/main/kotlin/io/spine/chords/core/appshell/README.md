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
[app](Application.kt) property, which provides access to certain standard
application-wide APIs from within any component.

See the details in the [Application](Application.kt) and 
[AppView](AppView.kt) KDocs.

### Theme customization

`Application` installs the compact Chords Material 3 theme around all window
and dialog content. Override `ApplicationTheme` to replace its color scheme,
typography, shapes, desktop dimensions, or interaction values. The default
theme selects its initial light or dark palette from the operating system
appearance. It does not observe later system appearance changes; override
`ApplicationTheme` when the application needs a live theme switch.

### Standard application view layout

Use [AppViewScaffold](AppViewScaffold.kt) for a conventional business
application screen with a heading, page actions, an optional toolbar, a main
work area, and an optional supporting details pane. Its dimensions and colors
follow the active theme and remain overridable at each usage site.

```kotlin
AppViewScaffold(
    title = "Customers",
    actions = {
        Button(onClick = ::createCustomer) {
            Text("New customer")
        }
    },
    toolbar = {
        SearchField()
        FilterButton()
    },
    supportingPane = {
        CustomerDetails(selectedCustomer)
    }
) {
    CustomersTable()
}
```

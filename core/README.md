## Spine Chords Core
Facilities and components for writing desktop applications with 
the [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) framework

## Using Spine Chords Core in a Gradle project

Add a dependency to the library as follows:
```kotlin
dependencies {
    implementation("io.spine.chords:spine-chords-core:$chordsVersion")
}
```

## Overview of library's facilities

Below is a high-level overview of what is included in the module for 
a quick start. You can also study the references for a more detailed
documentation on respective topics.

### Application's shell

Provides an API that simplifies creating Compose desktop applications that
follow a multiview UI organization.

Although many components in Chords libraries do not depend on how
the application is created on a high level, some components (particularly in
the Spine Chords Client library) expect that the application is created using
this Application Shell API.

See [appshell/README.md](src/main/kotlin/io/spine/chords/core/appshell/README.md)
for details.

### A notion of class-based components 

As an alternative to writing components as `@Composable` functions, as is
typical in Compose, this library also adds a possibility to write components
as classes. This adds certain possibilities for writing and using Compose 
components, while still maintaining an experience of using such components
that is very similar to using regular function-based ones.

This approach doesn't replace the function-based components, but adds
a possibility to write components using an object-oriented paradigm whenever
that appears more appropriate, while using such components interchangeably
with the usual function-based ones. The syntax of using such components is
similar that of function-based ones.
 
See the 
[Component](src/main/kotlin/io/spine/chords/core/Component.kt) class.

### Base classes for input components

### `InputComponent` — a base class for all input components in Chords libraries

The library introduces its own notion of an input component that is represented
by the [InputComponent](src/main/kotlin/io/spine/chords/core/InputComponent.kt)
class. Having such a common basis for input components standardizes the API
that is expected from all input components, and allows reusing it via
class inheritance without having to repeat it for each implementation.

Such a standardized API is in particular useful for a polymorphic (uniform)
usage of different input components in components like input forms, which have
to be configured with arbitrary sets of input components.

See the [InputComponent](src/main/kotlin/io/spine/chords/core/InputComponent.kt)
class, which includes such things as the property for edited value, validation
support, etc.

### `InputField` — a base class for text-based input components

This is a subclass of `InputComponent`, which simplifies creating input
components for entering arbitrary value types in text form. It includes support
such aspects of entry, as customizable parsing and formatting of respective
value types, input text validation, etc.

See the [InputField](src/main/kotlin/io/spine/chords/core/InputField.kt)
documentation for details.

### `DropdownSelector`

[DropdownSelector](src/main/kotlin/io/spine/chords/core/DropdownSelector.kt) is
a base class for creating another type of input components, where item selection
should be performed using a drop-down list.

### Facilities for addressing common app development needs

In addition to components, the library includes such facilities:

- A simple API to declare and detect key combinations, like this:
  ```kotlin
  if (keyEvent matches 'x'.key.typed) { /*...*/ }
  ```
  or
  ```kotlin
  Modifier.on(Ctrl(Enter.key).up) { /*...*/ }
  ```
  See the [Keystroke](src/main/kotlin/io/spine/chords/core/keyboard/Keystroke.kt)
  class.

- Extension functions to address common tasks or current shortcomings in
  Compose, like ensuring the usual focus traversal with the Tab key for text
  fields (see [Modifier.moveFocusOnTab()](src/main/kotlin/io/spine/chords/core/primitive/TextFieldExts.kt)).

- **Some simple components** that address common needs like
  [CheckboxWithText](src/main/kotlin/io/spine/chords/primitive/CheckboxWithText.kt),
  [RadioButtonWithText](src/main/kotlin/io/spine/chords/primitive/RadioButtonWithText.kt),
  or [WithTooltip](src/main/kotlin/io/spine/chords/layout/WithTooltip.kt).

- **More complex components** like
    [Wizard](src/main/kotlin/io/spine/chords/layout/Wizard.kt) and 
    [DropdownSelector](src/main/kotlin/io/spine/chords/DropdownSelector.kt). 

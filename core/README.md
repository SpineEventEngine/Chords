## Chords Core
Facilities and components for writing desktop applications with 
the [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) framework

Here is a high-level overview of what is included in the module for 
a quick start:

- **Application's shell.**

  Provides an API that simplifies creating Compose desktop applications that
  follow a multiview UI organization.

  See [appshell/README.md](src/main/kotlin/io/spine/chords/appshell/README.md)
  for details.

- **A notion of class-based components is introduced.** 

  Such components are written as classes instead of functions. This doesn't 
  replace the function-based components, but adds a possibility to write 
  components using an object-oriented paradigm whenever that appears more
  appropriate, while using such components interchangeably with the usual
  function-based ones. The syntax of using such components is similar that of
  function-based ones.
   
  See the 
  [Component](src/main/kotlin/io/spine/chords/Component.kt) class.

- **A base class for input components.**

  This helps standardize the API that is expected from all input components and
  reuse it via inheritance without having to repeat it for each implementation.
  Such a standardized API can especially be useful for a polymorphic (uniform)
  usage of different input components in components like input forms, which have
  to be configured with arbitrary sets of input components.

  See the [InputComponent](src/main/kotlin/io/spine/chords/InputComponent.kt)
  class, which includes such things as the property for edited value, validation
  support, etc.

- **Some facilities for addressing common app development needs are introduced**,
  for example:
  - A simple API to declare and detect key combinations, like this:
    ```kotlin
    if (keyEvent matches 'x'.key.typed) { /*...*/ }
    ```
    or
    ```kotlin
    Modifier.on(Ctrl(Enter.key).up) { /*...*/ }
    ```
    See the [Keystroke](src/main/kotlin/io/spine/chords/keyboard/Keystroke.kt)
    class.
  - Extension functions to address common tasks or current shortcomings in
    Compose, like ensuring the usual focus traversal with the Tab key for text
    fields (see [Modifier.moveFocusOnTab()](src/main/kotlin/io/spine/chords/primitive/TextFieldExts.kt)).

- **Some simple components** that address common needs like
  [CheckboxWithText](src/main/kotlin/io/spine/chords/primitive/CheckboxWithText.kt),
  [RadioButtonWithText](src/main/kotlin/io/spine/chords/primitive/RadioButtonWithText.kt),
  or [WithTooltip](src/main/kotlin/io/spine/chords/layout/WithTooltip.kt).

- **More complex components** like
    [Wizard](src/main/kotlin/io/spine/chords/layout/Wizard.kt) and 
    [DropdownSelector](src/main/kotlin/io/spine/chords/DropdownSelector.kt). 

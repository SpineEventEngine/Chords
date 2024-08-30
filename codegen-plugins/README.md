## `codegen-plugins` — a separate Gradle project that generates the Kotlin extensions for the Proto messages.

The separate Gradle project is needed because the ProtoData plugin, 
that generates the code, requires the newer version of Gradle 
comparing to 1DAM project.

It is assumed that the build of this project will be triggered
from the 1DAM project build, specifically before the `compileKotlin`
task of the `model` subproject of 1DAM, and executed 
in a separate process.

If the build of the `codegen-plugins` project fails, this also
causes the main build to fail.

### Modules

* `message-fields` — contains implementation of the ProtoData
plugin, that generates implementations of MessageField interface for 
the fields of command Proto messages. The code is also generated for 
types of command message fields, except for the types provided by Protobuf.

* `model` — the placeholder where the source code of the `model` 
subproject of 1DAM will be copied during the build in order to run
ProtoData plugins and generate the required code. The generated code 
will be copied back to the `generatedSources` folder of the `model`
subproject of 1DAM.

# `codegen-workspace`
Git submodule that refers the
[codegen_workspace](https://github.com/SpineEventEngine/Chords/tree/codegen_workspace) branch
of [Chords](https://github.com/SpineEventEngine/Chords) repo
and contains `codegen-workspace` Gradle project, in which 
the `spine-chords-codegen-plugins` can be applied to the Proto sources of another Gradle project
that is based on Spine `1.9.x`.

### Modules

- [workspace](workspace) — working-directory module that is used as a container 
  for te Proto source code, for which the codegen is to be performed.

See [codegen-plugins](./../plugins/README.md) for detail.

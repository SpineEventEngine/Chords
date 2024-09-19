# `codegen-workspace`
Git submodule that refers the
[codegen_workspace](https://github.com/SpineEventEngine/Chords/tree/codegen_workspace) branch
of [Chords](https://github.com/SpineEventEngine/Chords) repo
and contains `codegen-workspace` Gradle project, in which 
the `spine-chords-codegen-plugins` can be applied to the Proto sources of another Gradle project
that is based on Spine `1.9.x`.

The plugins are based on ProtoData that requires Spine version `2.0.x` 
with Gradle version `7.6.x` and therefore cannot be applied to the projects
on Spine `1.9.x` with Gradle `6.9.x` directly.

### Modules

- [workspace](workspace) — working-directory module that is used as a container 
  for te Proto source code, for which the codegen is to be performed.

See [codegen-plugins]() for detail.

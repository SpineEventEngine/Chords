# Design Restraint

Prefer the simplest design that fully satisfies the requirement. Add layers,
hierarchies, or polymorphism only when the present case needs them.

This guideline applies to every implementation skill. It does not replace the
public API and safety rules in [`AGENTS.md`](../../AGENTS.md).

## Rules

- **Abstract over existing cases.** Introduce a base type after two concrete
  implementors exist in the repository or changeset. An anticipated second
  implementor does not count; write it first and derive the shared shape.
- **Type parameters answer to a different test.** They have no implementors,
  so the two-case rule does not apply. Keep one only when it preserves a type
  relationship the signature would otherwise lose, even at one call site.
  Remove it when every use supplies one type or a concrete type loses no
  call-site safety.
- **Published extension points are an exception.** Chords exposes abstractions
  for consumers to extend; `io.spine.chords.core.Component` and `Props`-style
  configuration exist for this reason. One repository inheritor suffices when
  KDoc states the external-extension purpose; internal helpers get no exception.
- **Add no unused capability:** no single-implementation internal interface,
  uniformly instantiated type parameter, unset option, unsubclassed `open`, or
  forwarding-only layer. Every `public` declaration is a compatibility promise.
- **Give helpers a real responsibility.** Add a helper object for independent
  behavior, meaningful reuse, or state and lifecycle the caller should not manage.
  Do not extract one merely to shorten a class or wrap its private methods.
- **Keep the main workflow visible.** Extract complex details behind precise
  names, but prefer fewer concepts and transitions over smaller methods or files.
- **Add coordination only when required.** Concurrency, queues, overlap recovery,
  backpressure, and coordination messages need a current contract or concrete
  evidence; do not add them for speculative scale or failure modes.
- **Keep messages beside their behavior.** Extract a text-only constant only
  when the user explicitly requests it.
- **Extend existing shapes:** packages, modules, names, stack, Gradle, and the
  [component patterns](../skills/component-engineer/SKILL.md). Explain any parallel hierarchy.
- **Respect dependency direction.** Determine package and module boundaries
  from documentation and the surrounding design; an existing import or package
  name alone does not justify a dependency. Wire collaborators in an existing
  common layer when a direct dependency would cross those boundaries.
  Report violations encountered, including pre-existing ones, with the affected
  packages and code links. Mark uncertainty and keep fixes within the task's scope.
- **Fully solve the requirement.** Do not duplicate non-trivial logic, replace
  a root-cause fix with a workaround, or omit error handling to shrink the diff.

When simplicity and generality are genuinely balanced, take the option that is
cheaper to reverse, and note the trade-off in the final response.

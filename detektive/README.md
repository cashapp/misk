# Module: Detektive

Custom detekt (static analysis) rules used in Misk and in future other cashapp libraries.

Currently only contains the following rule:
- `AnnotatePublicApisWithJvmOverloads` - Enforces the presence of `@JvmOverloads` annotation on public constructors and functions with default arguments. This is to prevent binary incompatible changes through transitive dependencies when a new argument with default value is added to public APIs.

To suppress a rule violation on a specific class or function, annotate the element with `@Suppress("<RuleName>")` (see existing cases of `@Suppress("AnnotatePublicApisWithJvmOverloads")` as an example).
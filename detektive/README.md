# Module: Detektive

Custom detekt (static analysis) rules used i n Misk and in future other cashapp libraries.

Currently only contains the following rule:
- `AnnotatePublicApisWithJvmOverloads` - Enforces the presence of `@JvmOverloads` annotation on public constructors and functions with default arguments. This is to prevent binary incompatible changes through transitive dependencies when a new argument with default value is added to public APIs.
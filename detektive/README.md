# Module: Detektive

Custom Detekt 2.0.0-alpha.2 rules used in Misk. Rule implementations use the Kotlin Analysis API; Detekt's test fixtures require Kotlin compiler 2.3.0 even though Misk uses Kotlin 2.3.10.

`AnnotatePublicApisWithJvmOverloads` requires `@JvmOverloads` on public and `@PublishedApi internal` constructors and functions with default arguments. This preserves Java callers when a new defaulted parameter is added. Detekt autocorrection inserts the annotation; suppress individual violations with `@Suppress("AnnotatePublicApisWithJvmOverloads")`.
package misk.tracing

/**
 * Annotate a method with @Trace to enable tracing for the specified method.
 *
 * NOTE: This annotation is implemented using Guice's method interception capabilities.
 * As such, methods that can be traced using this annotation are subject to the same limitations
 * outlined in the <a href="https://github.com/google/guice/wiki/AOP">Guice wiki</a>.
 */
@Target(AnnotationTarget.FUNCTION)
annotation class Trace

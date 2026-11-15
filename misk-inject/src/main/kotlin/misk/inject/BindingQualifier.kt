package misk.inject

import kotlin.reflect.KClass

/**
 * Represents a binding qualifier for dependency injection.
 *
 * A binding qualifier distinguishes between multiple bindings of the same type by using annotations. This sealed
 * interface provides a type-safe way to work with both annotation types (classes) and annotation instances when
 * configuring Guice bindings.
 *
 * There are two types of qualifiers:
 * - [TypeClassifier]: Uses an annotation type (KClass) as the qualifier. This is useful when the annotation has no
 *   properties or when any instance of that annotation type should match.
 * - [InstanceQualifier]: Uses a specific annotation instance as the qualifier. This is necessary when the annotation
 *   has properties that distinguish different bindings (e.g., `@Named("foo")` vs `@Named("bar")`).
 *
 * Example usage:
 * ```kotlin
 * // Using annotation type
 * val typeQualifier = MyAnnotation::class.qualifier
 * newMultibinder<MyService>(typeQualifier)
 *
 * // Using annotation instance
 * val instanceQualifier = Named("production").qualifier
 * newMultibinder<MyService>(instanceQualifier)
 * ```
 *
 * @see TypeClassifier
 * @see InstanceQualifier
 */
sealed interface BindingQualifier {
  /**
   * A qualifier that uses an annotation type (class) to distinguish bindings.
   *
   * Use this when the annotation type itself is sufficient to identify the binding, without needing to consider the
   * annotation's property values.
   *
   * @property type The annotation class used as the qualifier
   */
  data class TypeClassifier(val type: KClass<out Annotation>) : BindingQualifier

  /**
   * A qualifier that uses a specific annotation instance to distinguish bindings.
   *
   * Use this when the annotation has properties that are significant for distinguishing between bindings. For example,
   * `@Named("foo")` and `@Named("bar")` are different instance qualifiers even though they share the same annotation
   * type.
   *
   * @property annotation The annotation instance used as the qualifier
   */
  data class InstanceQualifier(val annotation: Annotation) : BindingQualifier

  companion object {
    /**
     * Creates a [TypeClassifier] from an annotation type.
     *
     * @param type The annotation class to use as a qualifier
     * @return A [BindingQualifier] representing the annotation type
     */
    fun of(type: KClass<out Annotation>): BindingQualifier = TypeClassifier(type)

    /**
     * Creates an [InstanceQualifier] from an annotation instance.
     *
     * @param annotation The annotation instance to use as a qualifier
     * @return A [BindingQualifier] representing the annotation instance
     */
    fun of(annotation: Annotation): BindingQualifier = InstanceQualifier(annotation)
  }
}

/**
 * Extension property to convert an annotation class to a [BindingQualifier.TypeClassifier].
 *
 * This provides a convenient way to create a type-based qualifier from an annotation class.
 *
 * Example:
 * ```kotlin
 * val qualifier = MyAnnotation::class.qualifier
 * ```
 */
val KClass<out Annotation>.qualifier: BindingQualifier
  get() = BindingQualifier.TypeClassifier(this)

/**
 * Extension property to convert an annotation instance to a [BindingQualifier.InstanceQualifier].
 *
 * This provides a convenient way to create an instance-based qualifier from an annotation.
 *
 * Example:
 * ```kotlin
 * val qualifier = Named("production").qualifier
 * ```
 */
val Annotation.qualifier: BindingQualifier
  get() = BindingQualifier.InstanceQualifier(this)

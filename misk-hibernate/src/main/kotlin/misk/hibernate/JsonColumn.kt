package misk.hibernate

/**
 * Put this on a column field to get Hibernate to persist it as JSON using Moshi. It will use the injector's configured
 * Moshi instance. Use [misk.moshi.MoshiModule] to customize.
 *
 * It is an error to put this annotation on a mutable field. We don't defensively copy these when we read them out of
 * the database.
 */
@Target(AnnotationTarget.FIELD) annotation class JsonColumn

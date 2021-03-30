package misk.crypto

import javax.inject.Qualifier
import kotlin.annotation.AnnotationRetention.RUNTIME

/**
 * This annotation is used to decorate the collection of service keys used in the service.
 *
 * This annotation can be useful for cases where one would need access to all service keys
 * as a [Map]<[KeyAlias], [KeyType]>
 */
@Qualifier
@Target(
  AnnotationTarget.FIELD,
  AnnotationTarget.VALUE_PARAMETER
)
@Retention(RUNTIME)
annotation class ServiceKeys

package misk.crypto

import javax.inject.Qualifier

/**
 * This annotation is used to decorate the collection of external data keys used in the service.
 *
 * External data keys are defined in the configuration as a [Map]<[KeyAlias], [KeyType]>.
 *
 * This annotation can be useful for cases where one would need access to all external data
 * keys available to the service.
 */
@Qualifier
@Target(
  AnnotationTarget.FIELD,
  AnnotationTarget.VALUE_PARAMETER
)
annotation class ExternalDataKeys()

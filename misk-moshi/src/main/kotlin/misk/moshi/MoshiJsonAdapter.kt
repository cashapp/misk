package misk.moshi

import jakarta.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
internal annotation class MoshiJsonAdapter

/**
 * Similar to [MoshiJsonAdapter], but these adapters will be added by addLast() instead of add().
 * 
 * See also: https://github.com/square/moshi#precedence
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
internal annotation class MoshiJsonLastAdapter

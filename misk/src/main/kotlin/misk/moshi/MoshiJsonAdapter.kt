package misk.moshi

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
internal annotation class MoshiJsonAdapter

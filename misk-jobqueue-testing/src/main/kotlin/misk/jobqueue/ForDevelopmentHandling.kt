package misk.jobqueue

import javax.inject.Qualifier

@Deprecated("Replace the dependency on misk-jobqueue-testing with testFixtures(misk-jobqueue)")
@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
internal annotation class ForDevelopmentHandling

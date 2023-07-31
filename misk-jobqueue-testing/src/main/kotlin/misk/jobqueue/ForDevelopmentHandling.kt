package misk.jobqueue

import com.google.inject.BindingAnnotation
import jakarta.inject.Qualifier

@Deprecated("Replace the dependency on misk-jobqueue-testing with testFixtures(misk-jobqueue)")
@Qualifier
@BindingAnnotation
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
internal annotation class ForDevelopmentHandling

package misk.jobqueue.sqs

import com.google.inject.BindingAnnotation
import jakarta.inject.Qualifier

@Qualifier
@BindingAnnotation
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
internal annotation class ForSqsHandling

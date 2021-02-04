package misk.jobqueue.sqs

import javax.inject.Qualifier

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
internal annotation class ForSqsHandling

@Qualifier
internal annotation class ForSqsReceiving

@Qualifier
internal annotation class ForSqsSending

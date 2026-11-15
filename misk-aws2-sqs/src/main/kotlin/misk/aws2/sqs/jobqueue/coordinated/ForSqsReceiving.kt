package misk.aws2.sqs.jobqueue.coordinated

import jakarta.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class ForSqsReceiving

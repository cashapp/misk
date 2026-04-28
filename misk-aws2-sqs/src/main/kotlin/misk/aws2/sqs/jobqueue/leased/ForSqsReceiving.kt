package misk.aws2.sqs.jobqueue.leased

import jakarta.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class ForSqsReceiving

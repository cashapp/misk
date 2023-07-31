package misk.jobqueue.sqs

import com.google.inject.BindingAnnotation
import jakarta.inject.Qualifier

@Qualifier
@BindingAnnotation
internal annotation class ForSqsReceiving

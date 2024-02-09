package misk.jobqueue.sqs

import misk.jobqueue.Job
import misk.jobqueue.JobHandler
import javax.inject.Singleton

/***
 * An abstract class that could be extended on the queue
 */
@Singleton
abstract class SqsJobHandler: JobHandler {

  fun delayJobForXSeconds(job: Job) {

  }

}

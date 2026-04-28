package misk.aws2.sqs.jobqueue.leased

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import misk.hibernate.Gid
import misk.hibernate.Session
import misk.jobqueue.JobQueue
import misk.jobqueue.QueueName
import misk.jobqueue.TransactionalJobQueue
import misk.logging.getLogger

@Singleton
@Deprecated(
  message =
    "This implementation is not strictly transactional since jobs are enqueued in a DB " +
      "post commit hook. If the enqueueing operation fails, the DB record exists but no job is enqueued. " +
      "Instead, replace with a standard JobQueue and pass an idempotency key to the enqueue() function, " +
      "persist this value inside the job handler and check if it exists before running the handler.",
  level = DeprecationLevel.WARNING,
  replaceWith = ReplaceWith("JobQueue", "misk.jobqueue.JobQueue"),
)
internal class SqsTransactionalJobQueue @Inject internal constructor(private val jobQueue: JobQueue) :
  TransactionalJobQueue {
  override fun enqueue(
    session: Session,
    queueName: QueueName,
    body: String,
    idempotenceKey: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>,
  ) {
    session.onPostCommit {
      log.info { "forwarding to ${queueName.value}" }
      jobQueue.enqueue(queueName, body, idempotenceKey, deliveryDelay, attributes)
    }
  }

  override fun enqueue(
    session: Session,
    gid: Gid<*, *>,
    queueName: QueueName,
    body: String,
    idempotenceKey: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>,
  ) = enqueue(session, queueName, body, idempotenceKey, deliveryDelay, attributes)

  companion object {
    private val log = getLogger<SqsTransactionalJobQueue>()
  }
}

package misk.jobqueue.sqs

import misk.hibernate.Gid
import misk.hibernate.Session
import misk.jobqueue.JobQueue
import misk.jobqueue.QueueName
import misk.jobqueue.TransactionalJobQueue
import misk.logging.getLogger
import misk.tokens.TokenGenerator
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

// Implements the TransactionalJobQueue interface by registering a post-commit hook that
// forwards the message to SQS. It is not truly transactional, and is intended only for
// development / staging while we build proper job forwarding from the replication stream,
// but works so long as there is no application failure between the commit and the forward
@Singleton
internal class SqsTransactionalJobQueue @Inject internal constructor(
  private val jobQueue: JobQueue,
  private val tokenGenerator: TokenGenerator
) : TransactionalJobQueue {
  override fun enqueue(
    session: Session,
    gid: Gid<*, *>,
    queueName: QueueName,
    body: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>
  ) = enqueue(session, gid, queueName,
      tokenGenerator.generate("sqst"), body, deliveryDelay, attributes)

  override fun enqueue(
    session: Session,
    queueName: QueueName,
    body: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>
  ) = enqueue(session, queueName, tokenGenerator.generate("sqst"), body, deliveryDelay, attributes)

  override fun enqueue(
    session: Session,
    queueName: QueueName,
    idempotenceKey: String,
    body: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>
  ) {
    session.onPostCommit {
      log.info { "forwarding to ${queueName.value}" }
      jobQueue.enqueue(queueName, idempotenceKey, body, deliveryDelay, attributes)
    }
  }

  override fun enqueue(
    session: Session,
    gid: Gid<*, *>,
    queueName: QueueName,
    idempotenceKey: String,
    body: String,
    deliveryDelay: Duration?,
    attributes: Map<String, String>
  ) = enqueue(session, queueName, idempotenceKey, body, deliveryDelay, attributes)

  companion object {
    private val log = getLogger<SqsTransactionalJobQueue>()
  }
}

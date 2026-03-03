package misk.jobqueue

import misk.hibernate.Gid
import misk.hibernate.Session
import java.time.Duration
import java.util.UUID

/**
 * A [TransactionalJobQueue] supports enqueueing messages atomically in conjunction with
 * making local database updates. With the transactional job queue, messages are written to
 * a spooling table that is on the same database shard as the application's database entities,
 * then forwards the messages asynchronously to the actual queueing system. Two variants
 * of the [enqueue] operation exist - one which writes to the application's main shard, and
 * another which writes to the shard on which a given entity group exists. Applications
 * will typically use tbe second variant, writing jobs to the local shard in the context
 * of a transaction that modifies the contents of an entity group.
 */
interface TransactionalJobQueue {
  /**
   * Enqueues a job to the database shard associated with the given entity group. Will
   * throw an exception if the session is associated with a different entity group.
   * @param session The database session to use in writing the job
   * @param gid The id of the entity group with which the job should be associated
   * @param queueName the name of the queue on which to place the job
   * @param body The body of the job; can be any arbitrary string - it is up to the enqueuer and
   * consumer to agree on the format of the body
   * @param idempotenceKey Client-assigned unique key, useful for application code to detect duplicate work.
   * Implementations are expected to _not_ perform any filtering based on this value, as it carries meaning only for
   * application code (i.e. any logic around this property should take place in [JobHandler]s).
   * Defaults to a randomly generated UUID when not explicitly set.
   * @param deliveryDelay If specified, the job will only become visible to the consumer after
   * the provided duration. Used for jobs that should delay processing for a period of time.
   * @param attributes Arbitrary contextual attributes associated with the job
   */
  fun enqueue(
    session: Session,
    gid: Gid<*, *>,
    queueName: QueueName,
    body: String,
    idempotenceKey: String = UUID.randomUUID().toString(),
    deliveryDelay: Duration? = null,
    attributes: Map<String, String> = mapOf()
  )

  /**
   * Enqueues a job to the primary (unaffiliated) database shard . Will throw an exception if the
   * session is associated with an entity group.
   * @param session The database session to use in writing the job
   * @param queueName the name of the queue on which to place the job
   * @param body The body of the job; can be any arbitrary string - it is up to the enqueuer and
   * consumer to agree on the format of the body
   * @param idempotenceKey Client-assigned unique key, useful for application code to detect duplicate work.
   * Implementations are expected to _not_ perform any filtering based on this value, as it carries meaning only for
   * application code (i.e. any logic around this property should take place in [JobHandler]s).
   * Defaults to a randomly generated UUID when not explicitly set.
   * @param deliveryDelay If specified, the job will only become visible to the consumer after
   * the provided duration. Used for jobs that should delay processing for a period of time.
   * @param attributes Arbitrary contextual attributes associated with the job
   */
  fun enqueue(
    session: Session,
    queueName: QueueName,
    body: String,
    idempotenceKey: String = UUID.randomUUID().toString(),
    deliveryDelay: Duration? = null,
    attributes: Map<String, String> = mapOf()
  )
}

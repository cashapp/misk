package misk.events

import misk.hibernate.Gid
import misk.hibernate.Session
import java.sql.Connection

/**
 * A [SpooledProducer] is a producer that writes events to a local spool stored within a
 * service's database. [SpooledProducer]s can be used to coordinate event publishing with
 * local database transactions. Events published to the pool are done within the application's
 * local database transaction; a rollback of the database transaction will also rollback
 * any events published to the spool. Events are asynchronously forwarded from the spool
 * to the event stream, and are done so through a [Producer] transaction.
 */
interface SpooledProducer {
  /**
   * Publishes events to a sharded spool.
   *
   * @param session Session for the database transaction the event should be spooled in.
   * @param groupRootId Entity group root ID for the event, used as the sharding key in the database,
   *   and will be passed to the mapping function configured for the spool to look up a partition
   *   key for the event.
   */
  fun publish(session: Session, groupRootId: Gid<*, *>, topic: Topic, vararg event: Event)

  /**
   * Publishes events to an unsharded spool.
   *
   * @param session Session for the database transaction the event should be spooled in.
   */
  fun publishUnsharded(session: Session, topic: Topic, vararg event: Event)

  /**
   * Publishes events to an unsharded spool.
   *
   * @param connection Connection for the database transaction the event should be spooled in.
   */
  fun publishUnsharded(connection: Connection, topic: Topic, vararg event: Event)
}

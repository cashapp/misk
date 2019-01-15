package misk.events

import misk.hibernate.Gid
import misk.hibernate.Session

/**
 * A [SpooledProducer] is a producer that writes events to a local spool stored within a
 * service's database. [SpooledProducer]s can be used to coordinate event publishing with
 * local database transactions. Events published to the pool are done within the application's
 * local database transaction; a rollback of the database transaction will also rollback
 * any events published to the spool. Events are asynchronously forwarded from the spool
 * to the event stream, and are done so through a [Producer] transaction.
 */
interface SpooledProducer {
  fun publish(session: Session, groupRootId: Gid<*, *>, topic: Topic, vararg event: Event)
  fun publishUnsharded(session: Session, topic: Topic, vararg event: Event)
}
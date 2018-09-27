package misk.events

/**
 * A [Producer] is used to send events to an event stream.
 */
interface Producer {
  /**
   * A producer [Transaction] is a unit of publishing. Producer transactions adhere to the
   * transactional concepts of isolation (events publishing within a transaction are only
   * visible to consumers once the transaction is committed) and atomicity (once committed, all
   * events within the transactions are made available to consumers - no events will be lost).
   *
   * Note that a Producer transaction is _not_ connected to a database transaction; it is solely
   * a transaction within the event streaming system. To coordinate event publishing with
   * local database transactions, use a [SpooledProducer] which stores the events in a local table
   * as part of the local database transaction.
   *
   * Transactions remaining outstanding until the application calls commit, or rollback, or until
   * a producer specific timeout occurs.
   */
  interface Transaction {
    fun publish(topic: Topic, vararg events: Event)
    fun commit()
    fun rollback()
  }

  fun beginTransaction(): Transaction
}
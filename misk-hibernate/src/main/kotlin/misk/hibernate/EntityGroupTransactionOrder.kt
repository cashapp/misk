package misk.hibernate

/**
 * Vitess multi shard transactions are guaranteed to be committed in the order that updates are
 * issued. So if you have entity groups A and B, you issue an update against A followed by an
 * update to B, then the transaction against the shard of A will be fully committed before the
 * transaction against the shard of B. You can use this fact to make cross shard transactions safe
 * by committing clean up tasks to the earlier shards.
 */
interface EntityGroupTransactionOrder {
  /**
   * Issues a "spurious"/empty update on the entity group root to enforce a transaction to be
   * started on that entity group.
   */
  fun <P: DbRoot<P>> start(session: Session, entityGroup: P)

  /**
   * Checks that the transaction of the "leader" entity group will commit before the transaction of
   * the "follower" entity group.
   */
  fun <P: DbRoot<P>> assertOrder(session: Session, leader: Id<P>, follower: Id<P>)

  /**
   * Suppress checks.
   *
   * Should be used like this to suppress checks of code that we deliberately do not need to be
   * safe:
   * <pre>
   * transactionOrder.pushSuppressChecks();
   * try {
   * // unsafe code
   * } finally {
   * transactionOrder.popSuppressChecks();
   * }
  </pre> *
   */
  fun pushSuppressChecks()

  /**
   * Enable checks again.
   */
  fun popSuppressChecks()
}


package misk.hibernate

import javax.inject.Inject

/**
 * This class changes the parent id of an entity group child (the customer id, for example).
 *
 * This may result in the entity group child migrating between shards as the child will always
 * end up on the same shard as the parent.
 */
class ShardMigrator {
  @Inject private lateinit var transacter: Transacter
  @Inject private lateinit var transactionOrderListener: EntityGroupTransactionOrder

  fun <P : DbRoot<P>, C : DbChild<P, C>> migrate(
    child: C,
    oldParentId: Id<P>,
    newParentId: Id<P>
  ): C {
    return transacter.transaction {
      transactionOrderListener!!.assertOrder(it, newParentId, oldParentId)
      updateParent(it, child, newParentId)
    }
  }

  private fun <P : DbRoot<P>, C : DbChild<P, C>> updateParent(
    session: Session,
    child: C,
    newParentId: Id<P>
  ): C {
    return transacter.transaction {
      val newChild = child.migrate(this, it, newParentId)
      session.delete(child)
      session.flush()
      session.save(newChild)
      newChild
    }
  }
}


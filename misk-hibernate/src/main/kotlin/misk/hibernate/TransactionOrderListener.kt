package misk.hibernate

import java.util.LinkedHashSet
import java.util.Objects
import javax.inject.Inject
import javax.inject.Singleton
import org.hibernate.Session
import org.hibernate.event.spi.AbstractEvent
import org.hibernate.event.spi.PostInsertEvent
import org.hibernate.event.spi.PostInsertEventListener
import org.hibernate.event.spi.PostUpdateEvent
import org.hibernate.event.spi.PostUpdateEventListener
import org.hibernate.persister.entity.EntityPersister

/**
 * Detects the order of writes through a Hibernate event interceptor. Install this in your
 * component/apps persistence event listeners.
 */
@Singleton
class TransactionOrderListener : PostInsertEventListener,
    PostUpdateEventListener,
    EntityGroupTransactionOrder {
  @Inject internal var cowritesDetector: CowritesDetector? = null

  private val state = ThreadLocal.withInitial<State> { State() }

  override fun onPostInsert(event: PostInsertEvent) {
    state.get().trackWrites(event)
  }

  override fun onPostUpdate(event: PostUpdateEvent) {
    state.get().trackWrites(event)
  }

  override fun requiresPostCommitHanding(persister: EntityPersister): Boolean {
    return false
  }

  override fun <P : DbRoot<P>> start(session: misk.hibernate.Session, entityGroup: P) {
    cowritesDetector!!.pushSuppressChecks()
    try {
      if (!isStarted(session, entityGroup)) {
        entityGroup.incrementEditCount()
        session.update(entityGroup)
        session.flush()
      }
    } finally {
      cowritesDetector!!.popSuppressChecks()
    }
  }

  private fun <P : DbRoot<P>> isStarted(
    session: misk.hibernate.Session,
    entityGroup: P
  ): Boolean {
    return state.get().isStarted(getHibernateSession(session), entityGroup.id)
  }

  private fun getHibernateSession(session: misk.hibernate.Session): Session {
    return session.hibernateSession
  }

  override fun <P : DbRoot<P>> assertOrder(
    session: misk.hibernate.Session,
    leader: Id<P>,
    follower: Id<P>
  ) {
    state.get().assertOrder(getHibernateSession(session), leader, follower)
  }

  override fun pushSuppressChecks() {
    state.get().pushSuppressChecks()
  }

  override fun popSuppressChecks() {
    state.get().popSuppressChecks()
  }

  fun <P : DbRoot<P>> mark(
    session: misk.hibernate.Session,
    entityGroupRoot: Id<P>
  ) {
    state.get().mark(session.hibernateSession, entityGroupRoot)
  }

  private class EntityGroupWrite (
    val entityGroupRootId: Id<*>,
    val stacktraceToFirstWrite: Exception?
  ) {

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || javaClass != other.javaClass) return false
      val that = other as EntityGroupWrite?
      return entityGroupRootId == that!!.entityGroupRootId
    }

    override fun hashCode(): Int {
      return Objects.hash(entityGroupRootId)
    }
  }

  private class State {
    private val order = LinkedHashSet<EntityGroupWrite>()
    private var currentSession: Session? = null
    private var suppressChecks = 0

    fun trackWrites(event: AbstractEvent) {
      mark(event.session, EntityGroups().entityGroupId(entity(event)))
    }

    private fun refreshState(session: Session) {
      if (session !== currentSession) {
        if (suppressChecks != 0) {
          throw IllegalStateException("Unbalanced pushSuppressChecks and popSuppressChecks")
        }
        currentSession = session
        order.clear()
      }
    }

    private fun entity(event: AbstractEvent): Any {
      return when (event) {
        is PostInsertEvent -> event.entity
        is PostUpdateEvent -> event.entity
        else -> throw AssertionError()
      }
    }

    fun mark(session: Session, entityGroupRoot: Id<*>?) {
      if (entityGroupRoot == null) return

      refreshState(session)

      if (!order.contains(EntityGroupWrite(entityGroupRoot, null))) {
        order.add(EntityGroupWrite(entityGroupRoot,
            Exception(String.format("Transaction on %s started here", entityGroupRoot))))
      }
    }

    fun assertOrder(session: Session, leader: Id<*>, follower: Id<*>) {
      if (suppressChecks > 0) return

      refreshState(session)

      var leaderFound = false
      for (entityGroupWrite in order) {
        if (entityGroupWrite.entityGroupRootId == leader) {
          leaderFound = true
        }
        if (entityGroupWrite.entityGroupRootId == follower) {
          if (!leaderFound) {
            throw IllegalStateException("Expected the transaction for $leader to have started " +
                "before the transaction of $follower. Re-order the transactions using " +
                "Shards::start on the entity group root you want to have a leading transaction " +
                "earlier.", entityGroupWrite.stacktraceToFirstWrite)
          }
          break
        }
      }
      if (!leaderFound) {
        throw IllegalStateException("Expected the transaction for $leader to have started by now")
      }
    }

    fun <P : DbRoot<P>> isStarted(session: Session, entityGroup: Id<P>): Boolean {
      refreshState(session)
      return order.contains(
          EntityGroupWrite(entityGroup, null))
    }

    fun pushSuppressChecks() {
      suppressChecks++
    }

    fun popSuppressChecks() {
      suppressChecks--
    }
  }
}


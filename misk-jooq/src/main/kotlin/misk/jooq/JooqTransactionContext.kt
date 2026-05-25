package misk.jooq

/**
 * Tracks the currently active [JooqSession] per [JooqTransacter] on the calling thread so that nested calls to
 * [transactionOrAmbient] reuse the outer transaction's session instead of opening a new one.
 *
 * Sessions are keyed by [JooqTransacter] so concurrent ambient transactions on different databases do not collide.
 */
object JooqTransactionContext {
  private val currentSessions = ThreadLocal<MutableMap<JooqTransacter, JooqSession>>()

  fun get(transacter: JooqTransacter): JooqSession? = currentSessions.get()?.get(transacter)

  fun <T> withSession(transacter: JooqTransacter, session: JooqSession, block: () -> T): T {
    val sessions = currentSessions.get() ?: mutableMapOf<JooqTransacter, JooqSession>().also { currentSessions.set(it) }
    val previous = sessions.put(transacter, session)
    try {
      return block()
    } finally {
      if (previous != null) {
        sessions[transacter] = previous
      } else {
        sessions.remove(transacter)
        if (sessions.isEmpty()) currentSessions.remove()
      }
    }
  }
}

/**
 * Runs [block] inside this transacter's ambient [JooqSession] if one is already active on the current thread; otherwise
 * opens a new transaction and publishes its session as ambient for the duration of [block].
 *
 * Use this from repository methods that may be invoked either standalone or from within an outer transaction, so each
 * path participates in a single commit boundary instead of nesting.
 */
fun <T> JooqTransacter.transactionOrAmbient(block: (JooqSession) -> T): T {
  val session = JooqTransactionContext.get(this)
  return if (session != null) {
    block(session)
  } else {
    transaction { newSession ->
      JooqTransactionContext.withSession(this@transactionOrAmbient, newSession) { block(newSession) }
    }
  }
}

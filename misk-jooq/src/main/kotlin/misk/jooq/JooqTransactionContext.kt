package misk.jooq

/**
 * Runs [block] inside [ambient] if supplied; otherwise opens a new transaction and passes its [JooqSession] to [block].
 *
 * Designed for repository methods that may run standalone or be called from within an outer transaction. Callers that
 * already hold a session pass it as [ambient] so the work participates in the outer commit boundary instead of opening
 * a nested transaction.
 *
 * ```
 * fun create(name: String, ambient: JooqSession? = null) =
 *   transacter.transactionOrAmbient(ambient) { session -> ... }
 *
 * transacter.transactionOrAmbient { outer -> repo.create("foo", ambient = outer) }
 * ```
 *
 * When [ambient] is non-null no new transaction is opened and retry/backoff is skipped — the outer transaction owns
 * those concerns. When [ambient] is null this delegates to [JooqTransacter.transaction] with default options.
 *
 * Coroutine-safe by construction: the active session is passed explicitly through the call chain rather than tracked in
 * thread-local state, so suspension and dispatcher hops cannot desynchronize the lookup from the underlying JDBC
 * connection.
 */
fun <T> JooqTransacter.transactionOrAmbient(ambient: JooqSession? = null, block: (JooqSession) -> T): T =
  if (ambient != null) block(ambient) else transaction(callback = block)

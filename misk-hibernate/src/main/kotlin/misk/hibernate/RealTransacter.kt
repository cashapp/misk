package misk.hibernate

import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import misk.backoff.ExponentialBackoff
import misk.concurrent.ExecutorServiceFactory
import misk.jdbc.CheckDisabler
import misk.vitess.Shard.Companion.SINGLE_SHARD_SET
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.jdbc.map
import misk.jdbc.uniqueString
import misk.logging.getLogger
import misk.vitess.Destination
import misk.vitess.Keyspace
import misk.vitess.Shard
import misk.vitess.TabletType
import org.hibernate.FlushMode
import org.hibernate.SessionFactory
import org.hibernate.StaleObjectStateException
import org.hibernate.exception.ConstraintViolationException
import org.hibernate.exception.LockAcquisitionException
import java.io.Closeable
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLIntegrityConstraintViolationException
import java.sql.SQLRecoverableException
import java.sql.SQLTransientException
import java.time.Duration
import java.util.EnumSet
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import javax.persistence.OptimisticLockException
import kotlin.reflect.KClass

private val logger = getLogger<RealTransacter>()

// Check was moved to misk.jdbc, keeping a type alias to prevent compile breakage for usages.
typealias Check = misk.jdbc.Check

internal class RealTransacter private constructor(
  private val qualifier: KClass<out Annotation>,
  private val sessionFactoryProvider: Provider<SessionFactory>,
  /**
   * Replica reads are directed to this SessionFactory.
   *
   * If it's not been provided the writer SessionFactory will be used.
   */
  private val readerSessionFactoryProvider: Provider<SessionFactory>?,
  private val config: DataSourceConfig,
  private val threadLatestSession: ThreadLocal<RealSession>,
  private val options: TransacterOptions,
  private val executorServiceFactory: ExecutorServiceFactory,
  private val shardListFetcher: ShardListFetcher,
  private val hibernateEntities: Set<HibernateEntity>
) : Transacter {

  constructor(
    qualifier: KClass<out Annotation>,
    sessionFactoryProvider: Provider<SessionFactory>,
    readerSessionFactoryProvider: Provider<SessionFactory>?,
    config: DataSourceConfig,
    executorServiceFactory: ExecutorServiceFactory,
    hibernateEntities: Set<HibernateEntity>
  ) : this(
      qualifier = qualifier,
      sessionFactoryProvider = sessionFactoryProvider,
      readerSessionFactoryProvider = readerSessionFactoryProvider,
      config = config,
      threadLatestSession = ThreadLocal(),
      options = TransacterOptions(),
      executorServiceFactory = executorServiceFactory,
      shardListFetcher = ShardListFetcher(),
      hibernateEntities = hibernateEntities
  ) {
    shardListFetcher.init(this, config, executorServiceFactory)
  }

  override fun config(): DataSourceConfig = config

  override fun entities(): Set<KClass<out DbEntity<*>>> {
    return hibernateEntities.map { it.entity }.toSet()
  }

  /**
   * Uses a dedicated thread to query Vitess for the database's current shards. This caches the
   * set of shards for 5 minutes.
   */
  class ShardListFetcher {
    private lateinit var supplier: Supplier<Future<out Set<Shard>>>

    fun init(
      transacter: Transacter,
      config: DataSourceConfig,
      executorServiceFactory: ExecutorServiceFactory
    ) {
      check(!this::supplier.isInitialized)

      if (!config.type.isVitess) {
        this.supplier = Suppliers.ofInstance(CompletableFuture.completedFuture(SINGLE_SHARD_SET))
      } else {
        val executorService = executorServiceFactory.single("shard-list-fetcher-%d")
        this.supplier = Suppliers.memoizeWithExpiration({
          // Needs to be fetched on a separate thread to avoid nested transactions
          executorService.submit(Callable<Set<Shard>> {
            transacter.transaction { session ->
              session.useConnection { connection ->
                connection.createStatement().use { s ->
                  val shards = s.executeQuery("SHOW VITESS_SHARDS")
                      .map { rs -> Shard.parse(rs.getString(1)) }
                      .toSet()
                  if (shards.isEmpty()) {
                    throw SQLRecoverableException("Failed to load list of shards")
                  }
                  shards
                }
              }
            }
          })
        }, 5, TimeUnit.MINUTES)
      }
    }

    fun cachedShards(): Set<Shard> = supplier.get().get()
  }

  private val sessionFactory
    get() = sessionFactoryProvider.get()

  override val inTransaction: Boolean
    get() = threadLatestSession.get()?.inTransaction ?: false

  override fun isCheckEnabled(check: Check): Boolean {
    return CheckDisabler.isCheckEnabled(check)
  }

  override fun <T> transaction(block: (session: Session) -> T): T {
    return transactionWithRetriesInternal {
      transactionInternalSession(block)
    }
  }

  override fun <T> replicaRead(block: (session: Session) -> T): T {
    check(!inTransaction) {
      "Can't do replica reads inside a transaction"
    }
    if (!options.readOnly) {
      return readOnly().replicaRead(block)
    }
    return transactionWithRetriesInternal {
      replicaReadWithoutTransactionInternalSession { session ->
        session.target(Destination(TabletType.REPLICA)) {
          // Full scatters are allowed on replica reads as you can increase availability by
          // adding additional replicas.
          session.withoutChecks(Check.FULL_SCATTER) {
            block(session)
          }
        }
      }
    }
  }

  private fun <T> transactionWithRetriesInternal(block: () -> T): T {
    require(options.maxAttempts > 0)

    val backoff = ExponentialBackoff(
        Duration.ofMillis(options.minRetryDelayMillis),
        Duration.ofMillis(options.maxRetryDelayMillis),
        Duration.ofMillis(options.retryJitterMillis)
    )
    var attempt = 0

    while (true) {
      try {
        attempt++
        val result = block()

        if (attempt > 1) {
          logger.info {
            "retried ${qualifier.simpleName} transaction succeeded (${attemptNote(attempt)})"
          }
        }

        return result
      } catch (e: Exception) {
        if (!isRetryable(e)) throw e

        if (attempt >= options.maxAttempts) {
          logger.info {
            "${qualifier.simpleName} recoverable transaction exception " +
                "(${attemptNote(attempt)}), no more attempts"
          }
          throw e
        }

        val sleepDuration = backoff.nextRetry()
        logger.info(e) {
          "${qualifier.simpleName} recoverable transaction exception " +
              "(${attemptNote(attempt)}), will retry after a $sleepDuration delay"
        }

        if (!sleepDuration.isZero) {
          Thread.sleep(sleepDuration.toMillis())
        }
      }
    }
  }

  /**
   * Returns a string describing the most recent attempt. This includes whether the attempt used the
   * same connection which might help in diagnosing stale data problems.
   */
  private fun attemptNote(attempt: Int): String {
    if (attempt == 1) return "attempt 1"
    val latestSession = threadLatestSession.get()!!
    if (latestSession.sameConnection) return "attempt $attempt, same connection"
    return "attempt $attempt, different connection"
  }

  private fun <T> transactionInternalSession(block: (session: RealSession) -> T): T {
    return withSession { session ->
      session.target(Destination(TabletType.MASTER)) {
        val transaction = session.hibernateSession.beginTransaction()!!
        try {
          val result = block(session)

          // Flush any changes to the database before commit
          session.hibernateSession.flush()
          session.preCommit()
          transaction.commit()
          session.postCommit()
          result
        } catch (e: Throwable) {
          var rethrow = e
          if (config.type == DataSourceType.TIDB) {
            rethrow = mapTidbException(e)
          }

          if (transaction.isActive) {
            try {
              transaction.rollback()
            } catch (suppressed: Exception) {
              rethrow.addSuppressed(suppressed)
            }
          }
          throw rethrow
        }
      }
    }
  }

  private fun mapTidbException(e: Throwable): Throwable {
    // Special handling for TiDB in optimistic transaction mode, because some queries that would
    // fail or wait would succeed in this mode, only to fail on COMMIT instead. Hibernate doesn't
    // know how to interpret these exceptions coming from COMMIT, so we unwrap manually.
    when {
      // uniqueness constraints are enforced at COMMIT rather than INSERT.
      e.cause is SQLIntegrityConstraintViolationException -> {
        val sqlException = e.cause as SQLIntegrityConstraintViolationException
        return ConstraintViolationException(sqlException.message, sqlException, "")
      }
      // write-write conflicts fail at COMMIT rather than waiting on a lock.
      e.cause is SQLException && isTidbWriteConflict(e.cause as SQLException) -> {
        val sqlException = e.cause as SQLException
        return ConstraintViolationException(sqlException.message, sqlException, "")
      }
      else -> return e
    }
  }

  private fun <T> replicaReadWithoutTransactionInternalSession(block: (session: RealSession) -> T): T {
    return withSession(reader()) { session ->
      check(session.isReadOnly()) {
        "Reads should be in a read only session"
      }
      try {
        block(session)
      } catch (e: Throwable) {
        throw e
      }
    }
  }

  private fun reader(): SessionFactory {
    if (readerSessionFactoryProvider != null) {
      return readerSessionFactoryProvider.get()
    }
    if (config.type.isVitess) {
      return sessionFactory
    }
    if (config.type == DataSourceType.COCKROACHDB || config.type == DataSourceType.TIDB) {
      return sessionFactory
    }
    error(
        "No reader is configured for replica reads, pass in both a writer and reader qualifier and the full " +
            "DataSourceClustersConfig into HibernateModule, like this:\n" +
            "\tinstall(HibernateModule(AppDb::class, AppReaderDb::class, config.data_source_clusters[\"name\"]))")
  }

  override fun retries(maxAttempts: Int): Transacter = withOptions(
      options.copy(maxAttempts = maxAttempts))

  override fun allowCowrites(): Transacter {
    val disableChecks = options.disabledChecks.clone()
    disableChecks.add(Check.COWRITE)
    return withOptions(
        options.copy(disabledChecks = disableChecks))
  }

  override fun noRetries(): Transacter = withOptions(options.copy(maxAttempts = 1))

  override fun readOnly(): Transacter = withOptions(options.copy(readOnly = true))

  private fun withOptions(options: TransacterOptions): Transacter =
      RealTransacter(
          qualifier = qualifier,
          sessionFactoryProvider = sessionFactoryProvider,
          readerSessionFactoryProvider = readerSessionFactoryProvider,
          threadLatestSession = threadLatestSession,
          options = options,
          config = config,
          executorServiceFactory = executorServiceFactory,
          shardListFetcher = shardListFetcher,
          hibernateEntities = hibernateEntities
      )

  private fun <T> withSession(
    sessionFactory: SessionFactory = this.sessionFactory,
    block: (session: RealSession) -> T
  ): T {
    val hibernateSession = sessionFactory.openSession()
    val realSession = RealSession(
        hibernateSession = hibernateSession,
        readOnly = options.readOnly,
        config = config,
        disabledChecks = options.disabledChecks,
        predecessor = threadLatestSession.get(),
        transacter = this
    )

    // Note that the RealSession is closed last so that close hooks run after the thread locals and
    // Hibernate Session have been released. This way close hooks can start their own transactions.
    realSession.use {
      hibernateSession.use {
        useSession(realSession) {
          return block(realSession)
        }
      }
    }
  }

  private fun isRetryable(th: Throwable): Boolean {
    return when (th) {
      is RetryTransactionException,
      is StaleObjectStateException,
      is LockAcquisitionException,
      is SQLRecoverableException,
      is SQLTransientException,
      is OptimisticLockException -> true
      is SQLException -> if (isMessageRetryable(th)) true else isCauseRetryable(th)
      else -> isCauseRetryable(th)
    }
  }

  private fun isMessageRetryable(th: SQLException) =
      isConnectionClosed(th) || isVitessTransactionNotFound(th) ||
          isCockroachRestartTransaction(th) || isTidbWriteConflict(th)

  /**
   * This is thrown as a raw SQLException from Hikari even though it is most certainly a
   * recoverable exception.
   * See com/zaxxer/hikari/pool/ProxyConnection.java:493
   */
  private fun isConnectionClosed(th: SQLException) =
      th.message.equals("Connection is closed")

  /**
   * We get this error as a MySQLQueryInterruptedException when a tablet gracefully terminates, we
   * just need to retry the transaction and the new master should handle it.
   *
   * ```
   * vttablet: rpc error: code = Aborted desc = transaction 1572922696317821557: not found (CallerID: )
   * ```
   */
  private fun isVitessTransactionNotFound(th: SQLException): Boolean {
    val message = th.message
    return message != null &&
        message.contains("vttablet: rpc error") &&
        message.contains("code = Aborted") &&
        message.contains("transaction") &&
        message.contains("not found")
  }

  /**
   * "Messages with the error code 40001 and the string restart transaction indicate that a
   * transaction failed because it conflicted with another concurrent or recent transaction
   * accessing the same data. The transaction needs to be retried by the client."
   * https://www.cockroachlabs.com/docs/stable/common-errors.html#restart-transaction
   */
  private fun isCockroachRestartTransaction(th: SQLException): Boolean {
    val message = th.message
    return th.errorCode == 40001 && message != null &&
        message.contains("restart transaction")
  }

  /**
   * "Transactions in TiKV encounter write conflicts". This can happen when optimistic transaction
   * mode is on. Conflicts are detected during transaction commit
   * https://docs.pingcap.com/tidb/dev/tidb-faq#error-9007-hy000-write-conflict
   */
  private fun isTidbWriteConflict(th: SQLException): Boolean {
    return th.errorCode == 9007
  }

  private fun isCauseRetryable(th: Throwable) = th.cause?.let { isRetryable(it) } ?: false

  // NB: all options should be immutable types as copy() is shallow.
  internal data class TransacterOptions(
    val maxAttempts: Int = 3,
    val disabledChecks: EnumSet<Check> = EnumSet.noneOf(Check::class.java),
    val minRetryDelayMillis: Long = 100,
    val maxRetryDelayMillis: Long = 500,
    val retryJitterMillis: Long = 400,
    val readOnly: Boolean = false
  )

  internal class RealSession(
    override val hibernateSession: org.hibernate.Session,
    private val readOnly: Boolean,
    private val config: DataSourceConfig,
    private val transacter: RealTransacter,
    var disabledChecks: Collection<Check>,
    predecessor: RealSession?
  ) : Session, Closeable {
    private val preCommitHooks = mutableListOf<() -> Unit>()
    private val postCommitHooks = mutableListOf<() -> Unit>()
    private val sessionCloseHooks = mutableListOf<() -> Unit>()
    private val rootConnection = hibernateSession.rootConnection
    internal val sameConnection = predecessor?.rootConnection == rootConnection
    internal var inTransaction = false

    init {
      if (readOnly) {
        hibernateSession.isDefaultReadOnly = true
        hibernateSession.hibernateFlushMode = FlushMode.MANUAL
      }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : DbEntity<T>> save(entity: T): Id<T> {
      check(!readOnly) { "Saving isn't permitted in a read only session." }
      return when (entity) {
        is DbChild<*, *> -> (hibernateSession.save(entity) as Gid<*, *>).id
        else -> hibernateSession.save(entity)
      } as Id<T>
    }

    override fun <T : DbEntity<T>> delete(entity: T) {
      check(!readOnly) {
        "Deleting isn't permitted in a read only session."
      }
      return hibernateSession.delete(entity)
    }

    override fun <T : DbEntity<T>> load(id: Id<T>, type: KClass<T>): T {
      return hibernateSession.get(type.java, id)
    }

    override fun <R : DbRoot<R>, T : DbSharded<R, T>> loadSharded(
      gid: Gid<R, T>,
      type: KClass<T>
    ): T {
      return hibernateSession.get(type.java, gid)
    }

    override fun <T : DbEntity<T>> loadOrNull(id: Id<T>, type: KClass<T>): T? {
      return hibernateSession.get(type.java, id)
    }

    override fun shards(): Set<Shard> = transacter.shardListFetcher.cachedShards()

    override fun shards(keyspace: Keyspace): Collection<Shard> {
      return if (!config.type.isVitess) {
        // We always return the single shard regardless what keyspace you want shards for
        SINGLE_SHARD_SET
      } else {
        shards().filter { it.keyspace == keyspace }
      }
    }

    override fun <T> target(shard: Shard, function: () -> T): T {
      return if (config.type.isVitess) {
        target(currentTarget().mergedWith(Destination(shard)), function)
      } else {
        function()
      }
    }

    internal fun <T> target(destination: Destination, function: () -> T): T {
      return if (config.type.isVitess) {
        val previous = currentTarget()
        use(previous.mergedWith(destination))
        try {
          function()
        } finally {
          use(previous)
        }
      } else {
        function()
      }
    }

    private fun use(destination: Destination) = useConnection { connection ->
      check(config.type.isVitess)
      connection.createStatement().use { statement ->
        if (config.type == DataSourceType.VITESS_MYSQL) {
          val catalog = if (destination.isBlank()) "@master" else destination.toString()
          connection.catalog = catalog
        } else {
          withoutChecks {
            val sql = if (destination.isBlank()) "USE" else "USE `$destination`"
            statement.execute(sql)
          }
        }
      }
    }

    private fun currentTarget(): Destination = useConnection { connection ->
      check(config.type.isVitess)
      connection.createStatement().use { statement ->
        val target = if (config.type == DataSourceType.VITESS_MYSQL) {
          connection.catalog
        } else {
          withoutChecks {
            statement.executeQuery("SHOW VITESS_TARGET").uniqueString()
          }
        }
        Destination.parse(target)
      }
    }

    override fun <T> useConnection(work: (Connection) -> T): T {
      return hibernateSession.doReturningWork(work)
    }

    internal fun preCommit() {
      preCommitHooks.forEach { preCommitHook ->
        // Propagate hook exceptions up to the transacter so that the the transaction is rolled
        // back and the error gets returned to the application.
        preCommitHook()
      }
    }

    override fun onPreCommit(work: () -> Unit) {
      preCommitHooks.add(work)
    }

    internal fun postCommit() {
      postCommitHooks.forEach { postCommitHook ->
        try {
          postCommitHook()
        } catch (th: Throwable) {
          throw PostCommitHookFailedException(th)
        }
      }
    }

    override fun onPostCommit(work: () -> Unit) {
      postCommitHooks.add(work)
    }

    override fun close() {
      sessionCloseHooks.forEach { sessionCloseHook ->
        sessionCloseHook()
      }
    }

    override fun onSessionClose(work: () -> Unit) {
      sessionCloseHooks.add(work)
    }

    override fun <T> withoutChecks(vararg checks: Check, body: () -> T): T {
      return CheckDisabler.withoutChecks(*checks) { body() }
    }

    override fun <T> disableChecks(checks: Collection<Check>, body: () -> T): T {
      return CheckDisabler.disableChecks(checks) { body() }
    }

    internal fun isReadOnly(): Boolean = readOnly

    /**
     * Returns the physical JDBC connection of this session. Hibernate creates one-time-use wrappers
     * around the physical connections that talk to the database. This unwraps those so we can
     * tell when a connection is involved in a stale data problem.
     */
    private val org.hibernate.Session.rootConnection: Connection
      get() {
        var result: Connection = doReturningWork { connection -> connection }
        while (result.isWrapperFor(Connection::class.java)) {
          val unwrapped = result.unwrap(Connection::class.java)
          if (unwrapped == result) break
          result = unwrapped
        }
        return result
      }
  }

  private inline fun <R> useSession(session: RealSession, block: () -> R): R {
    val previous = threadLatestSession.get()
    check(previous == null || !previous.inTransaction) { "Attempted to start a nested session" }

    threadLatestSession.set(session)
    session.inTransaction = true
    try {
      return block()
    } finally {
      session.inTransaction = false
    }
  }
}

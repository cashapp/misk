package misk.hibernate

import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import java.io.Closeable
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLIntegrityConstraintViolationException
import java.sql.SQLRecoverableException
import java.time.Duration
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import misk.backoff.ExponentialBackoff
import misk.backoff.RetryConfig
import misk.backoff.retry
import misk.concurrent.ExecutorServiceFactory
import misk.hibernate.advisorylocks.tryAcquireLock
import misk.hibernate.advisorylocks.tryReleaseLock
import misk.jdbc.CheckDisabler
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.jdbc.map
import misk.logging.getLogger
import misk.vitess.Destination
import misk.vitess.Keyspace
import misk.vitess.Shard
import misk.vitess.Shard.Companion.SINGLE_SHARD_SET
import org.hibernate.FlushMode
import org.hibernate.SessionFactory
import org.hibernate.StaleObjectStateException
import org.hibernate.exception.ConstraintViolationException
import org.hibernate.exception.LockAcquisitionException
import org.hibernate.resource.jdbc.spi.PhysicalConnectionHandlingMode

private val logger = getLogger<RealTransacter>()

// Check was moved to misk.jdbc, keeping a type alias to prevent compile breakage for usages.
typealias Check = misk.jdbc.Check

internal class RealTransacter
private constructor(
  private val qualifier: KClass<out Annotation>,
  private val sessionFactoryService: SessionFactoryService,
  /**
   * Replica reads are directed to this SessionFactory.
   *
   * If it's not been provided the writer SessionFactory will be used.
   */
  private val readerSessionFactoryService: SessionFactoryService?,
  private val config: DataSourceConfig,
  private val options: TransacterOptions,
  private val executorServiceFactory: ExecutorServiceFactory,
  private val shardListFetcher: ShardListFetcher,
  private val hibernateEntities: Set<HibernateEntity>,
) : Transacter {

  private val qualifierName = qualifier.simpleName ?: "hibernate"
  private val exceptionClassifier = HibernateExceptionClassifier(config.type)

  constructor(
    qualifier: KClass<out Annotation>,
    sessionFactoryService: SessionFactoryService,
    readerSessionFactoryService: SessionFactoryService?,
    config: DataSourceConfig,
    executorServiceFactory: ExecutorServiceFactory,
    hibernateEntities: Set<HibernateEntity>,
  ) : this(
    qualifier = qualifier,
    sessionFactoryService = sessionFactoryService,
    readerSessionFactoryService = readerSessionFactoryService,
    config = config,
    options = TransacterOptions(),
    executorServiceFactory = executorServiceFactory,
    shardListFetcher = ShardListFetcher(),
    hibernateEntities = hibernateEntities,
  ) {
    shardListFetcher.init(this, config, executorServiceFactory)
  }

  override fun config(): DataSourceConfig = config

  override fun entities(): Set<KClass<out DbEntity<*>>> {
    return hibernateEntities.map { it.entity }.toSet()
  }

  /**
   * Uses a dedicated thread to query Vitess for the database's current shards. This caches the set of shards for 5
   * minutes.
   */
  class ShardListFetcher {
    private lateinit var supplier: Supplier<Future<out Set<Shard>>>

    fun init(transacter: Transacter, config: DataSourceConfig, executorServiceFactory: ExecutorServiceFactory) {
      check(!this::supplier.isInitialized)

      if (!config.type.isVitess) {
        this.supplier = Suppliers.ofInstance(CompletableFuture.completedFuture(SINGLE_SHARD_SET))
      } else {
        // Add randomness to the executor service name, to avoid
        // contention with multiple ShardListFetchers/Transacters.
        val uuid = UUID.randomUUID().toString()
        val executorService = executorServiceFactory.single(nameFormat = "shard-list-fetcher-$uuid-%d")
        this.supplier =
          Suppliers.memoizeWithExpiration(
            {
              // Needs to be fetched on a separate thread to avoid nested transactions
              executorService.submit(
                Callable<Set<Shard>> {
                  transacter.transaction { session ->
                    session.useConnection { connection ->
                      connection.createStatement().use { s ->
                        val shards =
                          s.executeQuery("SHOW VITESS_SHARDS")
                            .map { rs -> Shard.parse(rs.getString(1)) }
                            .filterNotNull()
                            .toSet()
                        if (shards.isEmpty()) {
                          throw SQLRecoverableException("Failed to load list of shards")
                        }
                        shards
                      }
                    }
                  }
                }
              )
            },
            5,
            TimeUnit.MINUTES,
          )
      }
    }

    fun cachedShards(): Set<Shard> = supplier.get().get()
  }

  private val sessionFactory
    get() = sessionFactoryService.sessionFactory

  override val inTransaction: Boolean
    get() = sessionFactoryService.threadInTransaction.get()

  override fun isCheckEnabled(check: Check): Boolean {
    return CheckDisabler.isCheckEnabled(check)
  }

  override fun <T> transaction(block: (session: Session) -> T): T {
    return transactionWithRetriesInternal { transactionInternalSession(block) }
  }

  override fun <T> replicaRead(block: (session: Session) -> T): T {
    check(!inTransaction) { "Can't do replica reads inside a transaction" }
    if (!options.readOnly) {
      return readOnly().replicaRead(block)
    }

    if (!config.type.isVitess) {
      return transactionWithRetriesInternal {
        replicaReadWithoutTransactionInternalSession { session -> block(session) }
      }
    }

    return vitessReplicaRead(block)
  }

  private fun <T> vitessReplicaRead(block: (session: Session) -> T): T {
    // Use connection holding to prevent connection changes during nested target operations
    return transactionWithRetriesInternal {
      val connectionHandlingMode = PhysicalConnectionHandlingMode.IMMEDIATE_ACQUISITION_AND_HOLD

      withNewHibernateSession(reader(), connectionHandlingMode) { hibernateSession ->
        val session =
          RealSession(
            hibernateSession = hibernateSession,
            readOnly = true,
            config = config,
            disabledChecks = options.disabledChecks,
            transacter = this,
          )

        session.use {
          useSession(session) {
            // target `@replica` if the datasource does not originate from a Reader config.
            if (config.database != Destination.replica().toString()) {
              session.target(Destination.replica(), block)
            } else {
              // otherwise just run the block, which avoids an unnecessary shard target.
              block(session)
            }
          }
        }
      }
    }
  }

  override fun <T> withLock(lockKey: String, block: () -> T): T {
    // This connection handling mode is required to use advisory locks sanely. Without this, hibernate will release the
    // java.sql.Connection underlying the org.hibernate.Session back to the connection pool after each transaction,
    // which means any subsequent transaction that gets that connection will think it holds the lock, when it does not.
    val connectionHandlingMode = PhysicalConnectionHandlingMode.IMMEDIATE_ACQUISITION_AND_HOLD
    return withNewHibernateSession(sessionFactory, connectionHandlingMode) { hibernateSession ->
      val primaryTarget = Destination.primary().toString()
      var previousTarget: String? = null
      if (config.type.isVitess) {
        hibernateSession.doReturningWork { conn ->
          previousTarget = conn.catalog
          if (previousTarget != primaryTarget) {
            conn.catalog = primaryTarget
          }
        }
      }

      val didAcquireLock = hibernateSession.tryAcquireLock(lockKey)
      check(didAcquireLock) { "Unable to acquire lock $lockKey" }

      if (config.type.isVitess && previousTarget != primaryTarget) {
        // Restore previous destination if it is not primary already.
        hibernateSession.doReturningWork { conn -> conn.catalog = previousTarget }
      }

      val blockResult = runCatching { block() }

      try {
        if (config.type.isVitess) {
          // Restore to the same destination the lock was acquired on.
          hibernateSession.doReturningWork { conn ->
            previousTarget = conn.catalog
            if (previousTarget != primaryTarget) {
              conn.catalog = primaryTarget
            }
          }
        }
        hibernateSession.tryReleaseLock(lockKey)
        if (config.type.isVitess && previousTarget != primaryTarget) {
          // Restore to the same destination the lock was acquired on.
          hibernateSession.doReturningWork { conn -> conn.catalog = previousTarget }
        }
      } catch (e: Throwable) {
        val originalFailure = blockResult.exceptionOrNull()?.apply { addSuppressed(e) }
        throw originalFailure ?: e
      }

      blockResult.getOrThrow()
    }
  }

  private fun <T> transactionWithRetriesInternal(block: () -> T): T {
    val backoff = ExponentialBackoff(
      baseDelay = Duration.ofMillis(options.minRetryDelayMillis),
      maxDelay = Duration.ofMillis(options.maxRetryDelayMillis),
      jitter = Duration.ofMillis(options.retryJitterMillis)
    )
    val retryConfig = RetryConfig.Builder(options.maxAttempts, backoff)
      .shouldRetry { exceptionClassifier.isRetryable(it) }
      .onRetry { attempt, e -> logger.info(e) { "$qualifierName transaction failed, retrying (attempt $attempt)" } }
      .build()
    return retry(retryConfig) { block() }
  }

  private fun <T> transactionInternalSession(block: (session: RealSession) -> T): T {
    return withSession { session ->
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
            session.onSessionClose { session.runRollbackHooks(e) }
          } catch (suppressed: Exception) {
            rethrow.addSuppressed(suppressed)
          }
        }
        throw rethrow
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
      // Error 9007: "Transactions in TiKV encounter write conflicts"
      // https://docs.pingcap.com/tidb/dev/tidb-faq#error-9007-hy000-write-conflict
      e.cause is SQLException && (e.cause as SQLException).errorCode == 9007 -> {
        val sqlException = e.cause as SQLException
        return ConstraintViolationException(sqlException.message, sqlException, "")
      }
      else -> return e
    }
  }

  private fun <T> replicaReadWithoutTransactionInternalSession(block: (session: RealSession) -> T): T {
    return withSession(reader()) { session ->
      check(session.isReadOnly()) { "Reads should be in a read only session" }
      try {
        block(session)
      } catch (e: Throwable) {
        throw e
      }
    }
  }

  private fun reader(): SessionFactory {
    if (readerSessionFactoryService != null) {
      return readerSessionFactoryService.sessionFactory
    }

    if (config.type == DataSourceType.COCKROACHDB || config.type == DataSourceType.TIDB) {
      return sessionFactory
    }
    error(
      "No reader is configured for replica reads, pass in both a writer and reader qualifier " +
        "and the full DataSourceClustersConfig into HibernateModule, like this:\n" +
        "\tinstall(HibernateModule(AppDb::class, AppReaderDb::class, " +
        "config.data_source_clusters[\"name\"]))"
    )
  }

  override fun retries(maxAttempts: Int): Transacter = withOptions(options.copy(maxAttempts = maxAttempts))

  override fun allowCowrites(): Transacter {
    val disableChecks = options.disabledChecks.clone()
    disableChecks.add(Check.COWRITE)
    return withOptions(options.copy(disabledChecks = disableChecks))
  }

  override fun noRetries(): Transacter = withOptions(options.copy(maxAttempts = 1))

  override fun readOnly(): Transacter = withOptions(options.copy(readOnly = true))

  private fun withOptions(options: TransacterOptions): Transacter =
    RealTransacter(
      qualifier = qualifier,
      sessionFactoryService = sessionFactoryService,
      readerSessionFactoryService = readerSessionFactoryService,
      options = options,
      config = config,
      executorServiceFactory = executorServiceFactory,
      shardListFetcher = shardListFetcher,
      hibernateEntities = hibernateEntities,
    )

  /**
   * Creates a new Hibernate [org.hibernate.Session] and stashes it in a [ThreadLocal] Note that this does *not* start a
   * transaction on the created session.
   *
   * @param sessionFactory The session factory to use when creating the session
   * @param connectionHandlingMode The Hibernate connection handling mode the new session will use. You should change
   *   this only for a specific use case, preferring the default from Hibernate.
   */
  private fun <T> withNewHibernateSession(
    sessionFactory: SessionFactory = this.sessionFactory,
    connectionHandlingMode: PhysicalConnectionHandlingMode =
      sessionFactory.sessionFactoryOptions.physicalConnectionHandlingMode,
    block: (session: org.hibernate.Session) -> T,
  ): T {
    check(sessionFactoryService.threadLocalHibernateSession.get() == null) { "nested session" }
    val hibernateSession = sessionFactory.withOptions().connectionHandlingMode(connectionHandlingMode).openSession()
    sessionFactoryService.threadLocalHibernateSession.set(hibernateSession)
    try {
      hibernateSession.use {
        return block(hibernateSession)
      }
    } finally {
      sessionFactoryService.threadLocalHibernateSession.remove()
    }
  }

  private fun <T> withSession(
    sessionFactory: SessionFactory = this.sessionFactory,
    block: (session: RealSession) -> T,
  ): T {
    val threadLocalHibernateSession = sessionFactoryService.threadLocalHibernateSession.get()
    val openedNewSession = threadLocalHibernateSession == null
    val hibernateSession = threadLocalHibernateSession ?: sessionFactory.openSession()
    val realSession =
      RealSession(
        hibernateSession = hibernateSession,
        readOnly = options.readOnly,
        config = config,
        disabledChecks = options.disabledChecks,
        transacter = this,
      )

    // Note that the RealSession is closed last so that close hooks run after the thread locals and
    // Hibernate Session have been released. This way close hooks can start their own transactions.
    realSession.use {
      // If we opened a session, we also must close it.
      if (openedNewSession) {
        hibernateSession.use {
          useSession(realSession) {
            return block(realSession)
          }
        }
      } else {
        useSession(realSession) {
          return block(realSession)
        }
      }
    }
  }

  // NB: all options should be immutable types as copy() is shallow.
  internal data class TransacterOptions(
    val maxAttempts: Int = 3,
    val disabledChecks: EnumSet<Check> = EnumSet.noneOf(Check::class.java),
    val minRetryDelayMillis: Long = 100,
    val maxRetryDelayMillis: Long = 500,
    val retryJitterMillis: Long = 400,
    val readOnly: Boolean = false,
  )

  internal class RealSession(
    override val hibernateSession: org.hibernate.Session,
    private val readOnly: Boolean,
    private val config: DataSourceConfig,
    private val transacter: RealTransacter,
    var disabledChecks: Collection<Check>,
  ) : Session, Closeable {
    private val preCommitHooks = mutableListOf<() -> Unit>()
    private val postCommitHooks = mutableListOf<() -> Unit>()
    private val sessionCloseHooks = mutableListOf<() -> Unit>()
    private val rollbackHooks = mutableListOf<(error: Throwable) -> Unit>()

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
      }
        as Id<T>
    }

    override fun <T : DbEntity<T>> delete(entity: T) {
      check(!readOnly) { "Deleting isn't permitted in a read only session." }
      return hibernateSession.delete(entity)
    }

    override fun <T : DbEntity<T>> load(id: Id<T>, type: KClass<T>): T {
      return hibernateSession.get(type.java, id)
    }

    override fun <R : DbRoot<R>, T : DbSharded<R, T>> loadSharded(gid: Gid<R, T>, type: KClass<T>): T {
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

    @Deprecated("Use target(Destination, block) which has more explicit targeting")
    override fun <T> target(shard: Shard, function: () -> T): T = target(Destination(shard)) { _ -> function() }

    override fun <T> target(destination: Destination, block: (session: Session) -> T): T {
      return when (config.type.isVitess) {
        false -> block(this)
        true -> {
          useConnection { connection ->
            val previous = currentTarget(connection)
            val targetHasChanged = previous != destination
            try {
              if (targetHasChanged) {
                logger.debug {
                  "The new destination was updated from previous target. Destination target: $destination, " +
                    "previous target: $previous"
                }
                use(connection, destination)
              }
              block(this)
            } catch (e: Exception) {
              throw e
            } finally {
              if (targetHasChanged) {
                try {
                  use(connection, previous)
                } catch (e: Exception) {
                  logger.error(e) {
                    "Exception restoring destination, previous = $previous, destination = $destination, " +
                      "cause = ${e.message}"
                  }
                }
              }
            }
          }
        }
      }
    }

    private fun use(connection: Connection, destination: Destination) {
      check(config.type.isVitess)
      connection.createStatement().use { _ ->
        val catalog = if (destination.isBlank()) "${Destination.primary()}" else "$destination"
        connection.catalog = catalog
      }
    }

    private fun currentTarget(connection: Connection): Destination {
      check(config.type.isVitess)
      connection.createStatement().use { _ ->
        val target = connection.catalog
        return Destination.parse(target)
      }
    }

    override fun <T> useConnection(work: (Connection) -> T): T {
      return hibernateSession.doReturningWork(work)
    }

    internal fun preCommit() {
      preCommitHooks.forEach { preCommitHook ->
        // Propagate hook exceptions up to the transacter so that the transaction is rolled
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
      sessionCloseHooks.forEach { sessionCloseHook -> sessionCloseHook() }
    }

    override fun onSessionClose(work: () -> Unit) {
      sessionCloseHooks.add(work)
    }

    override fun onRollback(work: (error: Throwable) -> Unit) {
      rollbackHooks.add(work)
    }

    internal fun runRollbackHooks(error: Throwable) {
      rollbackHooks.forEach { rollbackHook -> rollbackHook(error) }
    }

    override fun <T> withoutChecks(vararg checks: Check, body: () -> T): T {
      return CheckDisabler.withoutChecks(*checks) { body() }
    }

    override fun <T> disableChecks(checks: Collection<Check>, body: () -> T): T {
      return CheckDisabler.disableChecks(checks) { body() }
    }

    internal fun isReadOnly(): Boolean = readOnly
  }

  private inline fun <R> useSession(session: RealSession, block: () -> R): R {
    check(!inTransaction) { "Attempted to start a nested session" }

    sessionFactoryService.threadInTransaction.set(true)
    try {
      return block()
    } finally {
      sessionFactoryService.threadInTransaction.set(false)
    }
  }
}

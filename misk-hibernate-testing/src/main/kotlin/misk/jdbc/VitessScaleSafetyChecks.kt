package misk.jdbc

import com.squareup.moshi.Moshi
import misk.moshi.adapter
import misk.okio.split
import mu.KotlinLogging
import net.ttddyy.dsproxy.ExecutionInfo
import net.ttddyy.dsproxy.QueryInfo
import net.ttddyy.dsproxy.listener.MethodExecutionContext
import net.ttddyy.dsproxy.listener.MethodExecutionListener
import net.ttddyy.dsproxy.listener.QueryExecutionListener
import net.ttddyy.dsproxy.proxy.ProxyConfig
import net.ttddyy.dsproxy.support.ProxyDataSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import java.io.EOFException
import java.sql.Connection
import java.util.Locale
import javax.inject.Singleton
import javax.sql.DataSource

internal data class Instruction(
  val Opcode: String,
  val Input: Instruction?
) {
  val isScatter: Boolean
    get() {
      return Opcode == "SelectScatter" || Input?.isScatter ?: false
    }
}

internal data class QueryPlan(
  val Original: String,
  val Instructions: Instruction,
  val ExecCount: Int
) {
  val isScatter: Boolean
    get() = Instructions.isScatter
}

internal data class Variables(
  /** Queries processed by plan type */
  val QueriesProcessed: Map<String, Int>
)

open class ExtendedQueryExectionListener : QueryExecutionListener, MethodExecutionListener {
  override fun beforeMethod(executionContext: MethodExecutionContext) {
    if (isStartTransaction(executionContext)) {
      beforeStartTransaction()
    }
    if (isRollbackTransaction(executionContext)) {
      doBeforeRollback()
    }
    if (isCommitTransaction(executionContext)) {
      doBeforeCommit()
    }
  }

  override fun afterMethod(executionContext: MethodExecutionContext) {
    if (isStartTransaction(executionContext)) {
      afterStartTransaction()
    }
    if (isRollbackTransaction(executionContext)) {
      doAfterRollback()
    }
    if (isCommitTransaction(executionContext)) {
      doAfterCommit()
    }
  }

  private fun isStartTransaction(executionContext: MethodExecutionContext) =
      executionContext.method.name == "setAutoCommit" && executionContext.methodArgs[0] == false

  private fun isRollbackTransaction(executionContext: MethodExecutionContext) =
      executionContext.method.name == "rollback"

  private fun isCommitTransaction(executionContext: MethodExecutionContext) =
      executionContext.method.name == "commit"

  final override fun beforeQuery(execInfo: ExecutionInfo?, queryInfoList: List<QueryInfo>?) {
    if (queryInfoList == null) return;

    for (info in queryInfoList) {
      val query = info.query.toLowerCase()
      if (query == "begin") {
        beforeStartTransaction()
      } else if (query == "commit") {
        doBeforeCommit()
      } else if (query == "rollback") {
        doBeforeRollback()
      } else {
        beforeQuery(query)
      }
    }
  }

  final override fun afterQuery(execInfo: ExecutionInfo?, queryInfoList: List<QueryInfo>?) {
    if (queryInfoList == null) return;

    for (info in queryInfoList) {
      val query = info.query.toLowerCase(Locale.ROOT)
      if (query == "begin") {
        afterStartTransaction()
      } else if (query == "commit") {
        doAfterCommit()
      } else if (query == "rollback") {
        doAfterRollback()
      } else {
        afterQuery(query)
      }
    }
  }

  private fun doBeforeCommit() {
    beforeEndTransaction()
    beforeCommitTransaction()
  }

  private fun doBeforeRollback() {
    try {
      beforeEndTransaction()
      beforeRollbackTransaction()
    } catch (e: Exception) {
      logger.error("Exception in before callback for rollback, " +
          "logging error instead of propagating so rollback can proceed", e)
    }
  }

  private fun doAfterCommit() {
    afterEndTransaction()
    afterCommitTransaction()
  }

  private fun doAfterRollback() {
    afterEndTransaction()
    afterRollbackTransaction()
  }

  protected open fun beforeRollbackTransaction() {}
  protected open fun beforeCommitTransaction() {}
  protected open fun beforeEndTransaction() {}
  protected open fun beforeStartTransaction() {}
  protected open fun beforeQuery(query: String) {}
  protected open fun afterRollbackTransaction() {}
  protected open fun afterCommitTransaction() {}
  protected open fun afterEndTransaction() {}
  protected open fun afterStartTransaction() {}
  protected open fun afterQuery(query: String) {}

  companion object {
    private val logger = KotlinLogging.logger {}
  }
}

/**
 * Throws a [FullScatterException] for scatter queries that doesn't have a lookup vindex.
 * Note: Current implementation is not thread safe and will not work in production.
 */
@Singleton
class VitessScaleSafetyChecks(
  val okHttpClient: OkHttpClient,
  val moshi: Moshi,
  val config: DataSourceConfig,
  val startVitessService: StartVitessService
) : DataSourceDecorator {

  private val fullScatterDetector = FullScatterDetector()
  private val crossEntityGroupTransactionDetector = CowriteDetector()
  val disabled = ThreadLocal.withInitial { false }

  private var connection: Connection? = null

  override fun decorate(dataSource: DataSource): DataSource {
    if (config.type != DataSourceType.VITESS) return dataSource

    val proxy = ProxyDataSource(dataSource)
    proxy.proxyConfig = ProxyConfig.Builder()
        .methodListener(fullScatterDetector)
        .methodListener(crossEntityGroupTransactionDetector)
        .build()
    proxy.addListener(fullScatterDetector)
    proxy.addListener(crossEntityGroupTransactionDetector)
    return proxy
  }

  inner class FullScatterDetector : ExtendedQueryExectionListener() {
    val count = ThreadLocal.withInitial { 0 }

    override fun beforeQuery(query: String) {
      if (disabled.get()) return

      count.set(extractScatterQueryCount())
    }

    override fun afterQuery(query: String) {
      if (disabled.get()) return

      val newScatterQueryCount = extractScatterQueryCount()
      if (newScatterQueryCount > count.get()) {
        throw FullScatterException(
            "Query scattered to all shards. This is expensive and prevents scalability because " +
                "we won't be able to decrease load on each shard by shard splitting. Please " +
                "introduce a lookup table vindex. Query was: $query")
      }
    }
  }

  inner class CowriteDetector : ExtendedQueryExectionListener() {
    private val keyspaceIdsThisTransaction: ThreadLocal<LinkedHashSet<String>> =
        ThreadLocal.withInitial { LinkedHashSet<String>() }

    override fun beforeStartTransaction() {
      // Connect before the query because connecting spits out a bunch of crap in the general_log
      // that makes it harder for us to get to the thread id
      connect()

      check(keyspaceIdsThisTransaction.get().isEmpty()) {
        "Transaction state has not been cleaned up, beforeEndTransaction was never executed"
      }
    }

    override fun afterQuery(query: String) {
      if (!isDml(query)) return

      val queryInDatabase = extractLastDmlQuery() ?: return

      val m = "vtgate:: keyspace_id:([^ ]+)".toRegex().find(queryInDatabase) ?: return
      val keyspaceId = m.groupValues[1]

      val keyspaceIds = keyspaceIdsThisTransaction.get()
      keyspaceIds.add(keyspaceId)

      if (keyspaceIds.size > 1) {
        throw CowriteException(
            "DML against more than one entity group in the same transaction. " +
                "These are not guaranteed to be ACID across shard splits and should be avoided. " +
                "Query was: $queryInDatabase")
      }
    }

    override fun beforeEndTransaction() {
      keyspaceIdsThisTransaction.get().clear()
    }
  }

  val COMMENT_PATTERN = "/\\*+[^*]*\\*+(?:[^/*][^*]*\\*+)*/".toRegex()
  val DML = setOf("insert", "delete", "update")

  private fun isDml(query: String): Boolean {
    val first = query
        .replace(COMMENT_PATTERN, "")
        .trimStart()
        .toLowerCase()
        .takeWhile { !it.isWhitespace() }
    return DML.contains(first)
  }

  /**
   * Digs into the MySQL log to find the last executed DML statement that passed through Vitess.
   */
  private fun extractLastDmlQuery(): String? {
    return connect().let { c ->
      c.createStatement().use { s ->
        // OFFSET 1 because this query itself is also directly logged before it's executed
        // "/* _stream" is appended to every DML statement by Vitess so that we skip a bunch of
        // irrelevant statements
        s.executeQuery("""
                  SELECT argument
                  FROM mysql.general_log
                  WHERE command_type = 'Query'
                  AND argument LIKE '%/* _stream%'
                  ORDER BY event_time DESC
                  LIMIT 1 OFFSET 1
                """.trimIndent())
            .uniqueResult { it.getString(1) }
      }
    }
  }

  /**
   * Connects directly to the Docker Vitess mysqld, bypassing vtgate entirely. We use this to dig
   * into the query log. This is a perpetual, not connection pooled connection so should not be
   * closed. We shut down the Vitess docker container after the tests have completed running so
   * this doesn't need to be closed explicitly.
   */
  private fun connect(): Connection {
    var connection = connection;
    if (connection != null) return connection;

    val cluster = startVitessService.cluster()
    connection = cluster.openMysqlConnection()
    this.connection = connection
    return connection
  }

  /**
   * Figure out how many total full scatter queries we've executed so far.
   */
  private fun extractScatterQueryCount(): Int {
    val request = Request.Builder()
        .url("http://localhost:27000/debug/vars")
        .build()
    val response = okHttpClient.newCall(request).execute()
    val source = response.body()!!.source()
    val adapter = moshi.adapter<Variables>()
    val variables = adapter.fromJson(source)!!
    return variables.QueriesProcessed["SelectScatter"] ?: 0
  }

  /**
   * We don't use this anymore but we may want to introspect the query plans again in the future
   * so I'm leaving it around for now.
   */
  private fun extractScatterQueryCountFromPlan(): Int {
    val request = Request.Builder()
        .url("http://localhost:27000/debug/query_plans")
        .build()
    val response = okHttpClient.newCall(request).execute()
    val source = response.body()!!.source()
    return parseQueryPlans(source).filter { it.isScatter }.sumBy { it.ExecCount }
  }

  private val EMPTY_LINE = "\n\n".encodeUtf8()

  internal fun parseQueryPlans(data: BufferedSource): Sequence<QueryPlan> {
    // Read (and discard) the "Length" line
    data.readUtf8Line()

    val adapter = moshi.adapter<QueryPlan>()

    return data.split(EMPTY_LINE).map { buffer ->
      // Discard top line
      buffer.readUtf8Line()
      try {
        adapter.fromJson(buffer)
      } catch (e: EOFException) {
        null
      }
    }.filterNotNull()
  }

  fun <T> disable(body: () -> T): T = disabled.withValue(true, body)
}

private inline fun <T, R> ThreadLocal<T>.withValue(value: T, body: () -> R): R {
  val prev = get()
  set(value)
  try {
    return body()
  } finally {
    set(prev)
  }
}

/**
 * Returns a list containing elements after the last element that satisfied the given [predicate]
 * or the empty list if no elements satisfy the predicate.
 */
inline fun <T> List<T>.takeLastFrom(predicate: (T) -> Boolean): List<T> {
  if (isEmpty())
    return emptyList()
  val iterator = listIterator(size)
  while (iterator.hasPrevious()) {
    if (predicate(iterator.previous())) {
      val expectedSize = size - iterator.nextIndex()
      if (expectedSize == 0) return emptyList()
      return ArrayList<T>(expectedSize).apply {
        while (iterator.hasNext())
          add(iterator.next())
      }
    }
  }
  return emptyList()
}

fun String.quoteSql(escape: String): String =
    replace(escape, "" + escape + escape)
        .replace("%", "$escape%")
        .replace("_", "${escape}_")
        .replace("[", "$escape[")

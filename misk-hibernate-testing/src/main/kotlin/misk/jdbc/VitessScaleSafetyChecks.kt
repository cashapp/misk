package misk.jdbc

import com.google.common.collect.ImmutableSet
import com.squareup.moshi.Moshi
import misk.hibernate.Check
import misk.hibernate.Transacter
import misk.moshi.adapter
import misk.okio.split
import misk.vitess.StartVitessService
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
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.math.BigInteger
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.SQLSyntaxErrorException
import java.sql.Timestamp
import java.util.ArrayDeque
import java.util.Locale
import javax.inject.Singleton
import javax.sql.DataSource

private val vtgateKeyspaceIdRegex = "vtgate:: keyspace_id:([^ ]+)".toRegex()

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

/**
 * A MySQL query explanation.
 */
internal class Explanation {

  internal var id: BigInteger? = null
  internal var select_type: String? = null
  /**
   * The name of the table to which the row of output refers.
   */
  internal var table: String? = null
  /**
   * The partitions from which records would be matched by the query. The value is NULL for
   * nonpartitioned tables.
   */
  internal var partitions: String? = null
  /**
   * The join type. For descriptions of the different types, see
   * https://dev.mysql.com/doc/refman/5.7/en/explain-output.html#explain-join-types.
   */
  internal var type: String? = null
  /**
   * The possible_keys column indicates which indexes MySQL can choose from to find the rows in
   * this table. Note that this column is totally independent of the order of the tables as
   * displayed in the output from EXPLAIN. That means that some of the keys in possible_keys might
   * not be usable in practice with the generated table order.
   *
   * If this column is NULL (or undefined in JSON-formatted output), there are no relevant
   * indexes. In this case, you may be able to improve the performance of your query by examining
   * the WHERE clause to check whether it refers to some column or columns that would be suitable
   * for indexing.
   */
  internal var possible_keys: String? = null
  /**
   * The key column indicates the key (index) that MySQL actually decided to use. If MySQL decides
   * to use one of the possible_keys indexes to look up rows, that index is listed as the key
   * value.
   *
   * It is possible that key will name an index that is not present in the possible_keys value.
   * This can happen if none of the possible_keys indexes are suitable for looking up rows, but
   * all the columns selected by the query are columns of some other index. That is, the named
   * index covers the selected columns, so although it is not used to determine which rows to
   * retrieve, an index scan is more efficient than a data row scan.
   *
   * For InnoDB, a secondary index might cover the selected columns even if the query also selects
   * the primary key because InnoDB stores the primary key value with each secondary index. If key
   * is NULL, MySQL found no index to use for executing the query more efficiently.
   */
  internal var key: String? = null
  /**
   * The key_len column indicates the length of the key that MySQL decided to use. The value of
   * key_len enables you to determine how many parts of a multiple-part key MySQL actually uses.
   * If the key column says NULL, the ken_len column also says NULL.
   *
   * Due to the key storage format, the key length is one greater for a column that can be NULL
   * than for a NOT NULL column.
   */
  internal var key_len: String? = null
  /**
   * The ref column shows which columns or constants are compared to the index named in the key
   * column to select rows from the table.
   *
   * If the value is func, the value used is the result of some function. To see which function,
   * use SHOW WARNINGS following EXPLAIN to see the extended EXPLAIN output. The function might
   * actually be an operator such as an arithmetic operator.
   */
  internal var ref: String? = null
  /**
   * The rows column indicates the number of rows MySQL believes it must examine to execute the
   * query.
   *
   * For InnoDB tables, this number is an estimate, and may not always be exact.
   */
  internal var rows: BigInteger? = null
  /**
   * The filtered column indicates an estimated percentage of table rows that will be filtered by
   * the table condition. That is, rows shows the estimated number of rows examined and
   * rows × filtered / 100 shows the number of rows that will be joined with previous tables.
   */
  internal var filtered: Double? = null
  /**
   * This column contains additional information about how MySQL resolves the query. For
   * descriptions of the different values, see
   * https://dev.mysql.com/doc/refman/5.7/en/explain-output.html#explain-extra-information.
   */
  internal var Extra: String? = null

  // MySQL chose a key, let's make sure it's in possible_keys. If not, the key isn't likely
  // going to be helpful.
  // Sometimes MySQL knows the answer without having to do any work.
  fun isIndexed(): Boolean {
    if (key != null && possible_keys != null) {
      if (possible_keys!!.contains(key!!)) {
        return true
      }
    }
    return Extra != null && NO_INDEX_NEEDED_MESSAGES.contains(Extra!!)
  }

  /**
   * If the query has possible keys, takes input joined from another subquery, or uses the
   * PRIMARY key to fetch a limited number of rows, allow with a warning.
   */
  internal fun isProbablyOkay(query: String): Boolean {
    if (possible_keys != null && !possible_keys!!.isEmpty()) {
      return true
    }

    if (table != null) {
      if (table!!.startsWith("<union")) {
        // {code}<unionM,N>{code}: The row refers to the union of the rows with id values of M
        // and N.
        return true
      }
      if (table!!.startsWith("<derived")) {
        // {code}<derivedN>{code}: The row refers to the derived table result for the row with an
        // id value of N. A derived table may result, for example, from a subquery in the FROM
        // clause.
        return true
      }
      if (table!!.startsWith("<subquery")) {
        // {code}<subqueryN>{code}: The row refers to the result of a materialized subquery for
        // the row with an id value of N.
        // See https://dev.mysql.com/doc/refman/5.7/en/subquery-materialization.html.
        return true
      }
    }

    return "PRIMARY" == key && query.contains(" limit ")
  }

  override fun toString(): String = buildString {
    for (field in fields) {
      val value = field.get(this@Explanation) ?: continue
      append(", ")
      append(field.name)
      append("=")
      append(value)
    }
    replace(0, 2, "Explanation{")
    append('}')
  }

  companion object {
    /**
     * Some explanations don't have index information but it's not a problem.  For example, in
     * tests when tables are empty and you call max(id), it'll say "No matching min/max row".
     *
     * See EXPLAIN Extra Information at https://dev.mysql.com/doc/refman/5.7/en/explain-output.html.
     */
    internal val NO_INDEX_NEEDED_MESSAGES = ImmutableSet.of(
        "const row not found",
        "Impossible HAVING",
        "Impossible WHERE",
        "Impossible WHERE noticed after reading const tables",
        "No matching min/max row",
        "no matching row in const table",
        "No matching rows after partition pruning",
        "No tables used",
        "Select tables optimized away",
        "unique row not found",
        "Using where; Open_frm_only; Scanned 0 databases"
    )

    fun fromResultSet(rs: ResultSet): Explanation {
      val result = Explanation()
      for (f in fields) {
        f.set(result, rs.getObject(f.name))
      }
      return result
    }

    private val fields: List<Field> =
        Explanation::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }.map {
          it.isAccessible = true
          it
        }
  }
}

open class ExtendedQueryExecutionListener : QueryExecutionListener, MethodExecutionListener {
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
    if (queryInfoList == null) return

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
    if (queryInfoList == null) return

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
  val startVitessService: StartVitessService,
  val transacter: Transacter
) : DataSourceDecorator {

  private val fullScatterDetector = FullScatterDetector()
  private val crossEntityGroupTransactionDetector = CowriteDetector()
  private val fullTableScanDetector = TableScanDetector()

  private var connection: Connection? = null
  private var vtgate: Connection? = null

  override fun decorate(dataSource: DataSource): DataSource {
    if (config.type != DataSourceType.VITESS && config.type != DataSourceType.VITESS_MYSQL) return dataSource

    val proxy = ProxyDataSource(dataSource)
    proxy.proxyConfig = ProxyConfig.Builder()
        .methodListener(fullScatterDetector)
        .methodListener(crossEntityGroupTransactionDetector)
        .methodListener(fullTableScanDetector)
        .build()
    proxy.addListener(fullScatterDetector)
    proxy.addListener(crossEntityGroupTransactionDetector)
    proxy.addListener(fullTableScanDetector)
    return proxy
  }

  inner class FullScatterDetector : ExtendedQueryExecutionListener() {
    private val count: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

    override fun beforeQuery(query: String) {
      if (!transacter.isCheckEnabled(Check.FULL_SCATTER)) return

      count.set(extractScatterQueryCount())
    }

    override fun afterQuery(query: String) {
      if (!transacter.isCheckEnabled(Check.FULL_SCATTER)) return

      val newScatterQueryCount = extractScatterQueryCount()
      if (newScatterQueryCount > count.get()) {
        throw FullScatterException(
            "Query scattered to all shards. This is expensive and prevents scalability because " +
                "we won't be able to decrease load on each shard by shard splitting. Please " +
                "introduce a lookup table vindex. Query was: $query")
      }
    }
  }

  inner class TableScanDetector : ExtendedQueryExecutionListener() {
    private val mysqlTimeBeforeQuery: ThreadLocal<Timestamp?> =
        ThreadLocal.withInitial { null }

    override fun beforeQuery(query: String) {
      if (!transacter.isCheckEnabled(Check.TABLE_SCAN)) return

      connect()?.let { c ->
        mysqlTimeBeforeQuery.set(c.createStatement().use { s ->
          s.executeQuery("SELECT MAX(event_time) FROM mysql.general_log")
              .map { it.getTimestamp(1) }
              .singleOrNull()
        })
      }
    }

    override fun afterQuery(query: String) {
      if (!transacter.isCheckEnabled(Check.TABLE_SCAN)) return

      val mysqlTime = mysqlTimeBeforeQuery.get() ?: return

      connect()?.let { c ->
        val queries = extractQueriesSince(mysqlTime)

        for (rawQuery in queries) {
          if (isDml(rawQuery)) return

          // Find the keyspaces where this query could potentially belong
          val potentialKeyspaces = cluster()!!.keyspaces()
              .filter { keyspace ->
                keyspace.value.tables.keys.any { table -> rawQuery.contains(table) }
              }

          if (potentialKeyspaces.isEmpty()) return

          val explanations = c.createStatement().use { s ->
            potentialKeyspaces.map(fun(keyspace): List<Explanation>? {
              val database = if (keyspace.value.sharded) {
                "vt_${keyspace.key}_-80"
              } else {
                "vt_${keyspace.key}_0"
              }
              s.execute("USE `$database`")
              return try {
                s.executeQuery("EXPLAIN ${rawQuery.replace("\n", " ")}")
                    .map { Explanation.fromResultSet(it) }
              } catch (e: SQLSyntaxErrorException) {
                // TODO(jontirsen): This happens during multi threaded tests, let's ignore it for
                //   now. Implement proper support for multi-threading at some point (it's hard).
                null
              } catch (e: SQLException) {
                val message = e.message
                if (message != null && message.matches(wrongDatabaseError)) null
                else throw e
              }
            }).filterNotNull().firstOrNull()
          } ?: return

          if (!explanations.all { it.isIndexed() }) {
            val plan = explanations.joinToString("\n")
            if (explanations.all { it.isProbablyOkay(rawQuery) }) {
              logger.warn { "Possibly missing index. Investigate query plan.\n$rawQuery\nPlan is:$plan" }
            } else {
              throw TableScanException("Missing index on query:\n$rawQuery\nPlan is:\n$plan")
            }
          }
        }
      }
    }
  }

  inner class CowriteDetector : ExtendedQueryExecutionListener() {

    private val transactionDeque: ThreadLocal<ArrayDeque<LinkedHashSet<String>>> =
        ThreadLocal.withInitial { ArrayDeque<LinkedHashSet<String>>() }

    override fun beforeStartTransaction() {
      // Connect before the query because connecting spits out a bunch of crap in the general_log
      // that makes it harder for us to get to the thread id
      connect()

      transactionDeque.get().push(LinkedHashSet())
    }

    override fun afterQuery(query: String) {
      if (!transacter.isCheckEnabled(Check.COWRITE)) return
      if (!isDml(query)) return

      val queryInDatabase = extractLastDmlQuery() ?: return

      val m = vtgateKeyspaceIdRegex.find(queryInDatabase) ?: return
      val keyspaceId = m.groupValues[1]

      val keyspaceIds = transactionDeque.get().peek()
      keyspaceIds.add(keyspaceId)

      if (keyspaceIds.size > 1) {
        throw CowriteException(
            "DML against more than one entity group in the same transaction. " +
                "These are not guaranteed to be ACID across shard splits and should be avoided. " +
                "Query was: $queryInDatabase")
      }
    }

    override fun beforeEndTransaction() {
      transactionDeque.get().pop()
    }
  }

  private val COMMENT_PATTERN = "/\\*+[^*]*\\*+(?:[^/*][^*]*\\*+)*/".toRegex()
  private val DML = setOf("insert", "delete", "update")

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
    return connect()?.let { c ->
      c.createStatement().use { s ->
        s.executeQuery("""
                  SELECT argument
                  FROM mysql.general_log
                  WHERE command_type = 'Query'
                  AND (
                    argument LIKE '%update%'
                    OR argument LIKE '%insert%'
                    OR argument LIKE '%delete%'
                  )
                  AND NOT argument LIKE 'SELECT argument%'
                  ORDER BY event_time DESC
                  LIMIT 1
                """.trimIndent())
            .uniqueString()
      }
    }
  }

  /**
   * Digs into the MySQL log to find the last executed DML statement that passed through Vitess.
   */
  private fun extractLastQuery(): String? {
    return connect()?.let { c ->
      c.createStatement().use { s ->
        s.executeQuery("""
                  SELECT argument
                  FROM mysql.general_log
                  WHERE command_type = 'Query'
                  AND NOT argument LIKE '%general_log%'
                  AND NOT argument = 'begin'
                  AND NOT argument LIKE '%1 != 1%'
                  ORDER BY event_time DESC
                  LIMIT 1
                """.trimIndent())
            .uniqueString()
      }
    }
  }

  /**
   * Digs into the MySQL log to find the last executed DML statement that passed through Vitess.
   */
  private fun extractQueriesSince(mysqlTime: Timestamp): List<String> {
    return connect()?.let { c ->
      c.prepareStatement("""
                  SELECT argument
                  FROM mysql.general_log
                  WHERE command_type = 'Query'
                  AND NOT argument LIKE '%general_log%'
                  AND NOT argument = 'begin'
                  AND NOT argument LIKE '%1 != 1%'
                  AND NOT lower(argument) LIKE '%information_schema%'
                  AND NOT lower(argument) LIKE 'use %'
                  AND NOT lower(argument) LIKE 'show %'
                  AND NOT lower(argument) LIKE 'describe %'
                  AND event_time > ?
                  ORDER BY event_time DESC
                """.trimIndent()).use { s ->
        s.setTimestamp(1, mysqlTime)
        s.executeQuery().map { it.getString(1) }
      }
    } ?: arrayListOf()
  }

  /**
   * Connects directly to the Docker Vitess mysqld, bypassing vtgate entirely. We use this to dig
   * into the query log. This is a perpetual, not connection pooled connection so should not be
   * closed. We shut down the Vitess docker container after the tests have completed running so
   * this doesn't need to be closed explicitly.
   */
  private fun connect(): Connection? {
    var connection = connection
    if (connection != null) return connection

    val cluster = cluster() ?: return null
    connection = cluster.openMysqlConnection()
    this.connection = connection
    return connection
  }

  private fun connectVtgate(): Connection? {
    var connection = vtgate
    if (connection != null) return connection

    val cluster = cluster() ?: return null
    connection = cluster.openVtgateConnection()
    this.vtgate = connection
    return connection
  }

  private fun cluster() = startVitessService.cluster()

  /**
   * Figure out how many total full scatter queries we've executed so far.
   */
  private fun extractScatterQueryCount(): Int {
    val request = Request.Builder()
        .url("http://localhost:27000/debug/vars")
        .build()
    val adapter = moshi.adapter<Variables>()
    val variables = okHttpClient.newCall(request).execute().use {
      adapter.fromJson(it.body!!.source())!!
    }
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
    return okHttpClient.newCall(request).execute().use { r ->
      parseQueryPlans(moshi, r.body!!.source()).filter { it.isScatter }.sumBy { it.ExecCount }
    }
  }

  companion object {
    private val logger = KotlinLogging.logger {}

    private val wrongDatabaseError = Regex("Table '.*' doesn't exist")

    private val EMPTY_LINE = "\n\n".encodeUtf8()

    internal fun parseQueryPlans(moshi: Moshi, data: BufferedSource): Sequence<QueryPlan> {
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
  }
}

/**
 * Returns a list containing elements after the last element that satisfied the given [predicate]
 * or the empty list if no elements satisfy the predicate.
 */
inline fun <T> List<T>.sublistAfterMatch(predicate: (T) -> Boolean): List<T> {
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

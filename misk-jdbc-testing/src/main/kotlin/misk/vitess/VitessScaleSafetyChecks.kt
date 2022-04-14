package misk.vitess

import com.squareup.moshi.Moshi
import misk.database.DockerVitessCluster
import misk.database.StartDatabaseService
import misk.jdbc.Check
import misk.jdbc.CheckDisabler
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceDecorator
import misk.jdbc.DataSourceType
import misk.jdbc.ExtendedQueryExecutionListener
import misk.jdbc.ScaleSafetyChecks
import misk.jdbc.uniqueString
import misk.moshi.adapter
import net.ttddyy.dsproxy.proxy.ProxyConfig
import net.ttddyy.dsproxy.support.ProxyDataSource
import okhttp3.OkHttpClient
import okhttp3.Request
import wisp.containers.ContainerUtil
import java.io.File
import java.sql.Connection
import java.sql.Timestamp
import java.util.ArrayDeque
import java.util.Collections
import java.util.regex.Matcher
import java.util.regex.Pattern
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

/**
 * Throws a [FullScatterException] for scatter queries that doesn't have a lookup vindex.
 * Note: Current implementation is not thread safe and will not work in production.
 */
@Singleton
class VitessScaleSafetyChecks(
  val config: DataSourceConfig,
  val okHttpClient: OkHttpClient,
  val moshi: Moshi,
  val startDatabaseService: StartDatabaseService
) : DataSourceDecorator {
  private var connection: Connection? = null

  private val fullScatterDetector = FullScatterDetector()
  private val crossEntityGroupTransactionDetector = CowriteDetector()
  private val fullTableScanDetector = TableScanDetector()

  override fun decorate(dataSource: DataSource): DataSource {
    if (config.type != DataSourceType.VITESS_MYSQL) return dataSource

    connect()?.let {
      ScaleSafetyChecks.turnOnSqlGeneralLogging(it)
    }

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
      if (!CheckDisabler.isCheckEnabled(Check.FULL_SCATTER)) return

      count.set(extractScatterQueryCount())
    }

    override fun afterQuery(query: String) {
      if (!CheckDisabler.isCheckEnabled(Check.FULL_SCATTER)) return

      val newScatterQueryCount = extractScatterQueryCount()
      if (newScatterQueryCount > count.get()) {
        throw FullScatterException(
          """
          Query scattered to all shards. This is expensive and prevents scalability because
          we won't be able to decrease load on each shard by shard splitting. Please
          introduce a lookup table vindex. Query was: $query
          """.trimIndent()
        )
      }
    }
  }

  inner class TableScanDetector : ExtendedQueryExecutionListener() {
    private val tablePattern = Collections.synchronizedMap<String, Pattern>(mutableMapOf())

    private val mysqlTimeBeforeQuery: ThreadLocal<Timestamp?> =
      ThreadLocal.withInitial { null }

    override fun beforeQuery(query: String) {
      if (!CheckDisabler.isCheckEnabled(Check.TABLE_SCAN)) return

      connect()?.let { connection ->
        mysqlTimeBeforeQuery.set(ScaleSafetyChecks.getLastLoggedCommand(connection))
      }
    }

    override fun afterQuery(query: String) {
      if (!CheckDisabler.isCheckEnabled(Check.TABLE_SCAN)) return
      val mysqlTime = mysqlTimeBeforeQuery.get() ?: return

      connect()?.let { c ->
        val queries = ScaleSafetyChecks.extractQueriesSince(c, mysqlTime)
        for (rawQuery in queries) {
          // Find the keyspaces where this query could potentially belong
          val potentialKeyspaces = cluster()!!.keyspaces().filter { keyspace ->
            keyspace.value.tables.keys.any { table -> containsTable(rawQuery, table) }
          }

          for ((name, keyspace) in potentialKeyspaces) {
            val database = if (keyspace.sharded) {
              "vt_${name}_-80"
            } else {
              "vt_${name}_0"
            }

            ScaleSafetyChecks.checkQueryForTableScan(c, database, rawQuery)
          }
        }
      }
    }

    private fun containsTable(query: String, table: String): Boolean {
      val pattern = tablePattern.computeIfAbsent(table) {
        val pattern = "\\b$it\\b"
        Pattern.compile(pattern)
      }

      val m: Matcher = pattern.matcher(query)
      return m.find()
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
      if (!CheckDisabler.isCheckEnabled(Check.COWRITE)) return
      if (!ScaleSafetyChecks.isDml(query)) return

      val queryInDatabase = extractLastDmlQuery() ?: return

      val m = vtgateKeyspaceIdRegex.find(queryInDatabase) ?: return
      val keyspaceId = m.groupValues[1]

      val keyspaceIds = transactionDeque.get().peek()
      keyspaceIds.add(keyspaceId)

      if (keyspaceIds.size > 1) {
        throw CowriteException(
          """
          DML against more than one entity group in the same transaction.
          These are not guaranteed to be ACID across shard splits and should be avoided.
          Query was: $queryInDatabase    
          """.trimIndent()
        )
      }
    }

    override fun beforeEndTransaction() {
      transactionDeque.get().pop()
    }
  }

  /**
   * Connects directly to the Docker Vitess mysqld, bypassing vtgate entirely. We use this to dig
   * into the query log. This is a perpetual, not connection pooled connection so should not be
   * closed. We shut down the Vitess docker container after the tests have completed running so
   * this doesn't need to be closed explicitly.
   */
  fun connect(): Connection? {
    var connection = connection
    if (connection != null) return connection

    val cluster = cluster() ?: return null
    connection = cluster.openMysqlConnection()
    this.connection = connection
    return connection
  }

  private fun cluster() = startDatabaseService.server?.let { (it as DockerVitessCluster).cluster }

  /**
   * Figure out how many total full scatter queries we've executed so far.
   */
  private fun extractScatterQueryCount(): Int {
    val request = Request.Builder()
      .url("http://${ContainerUtil.dockerTargetOrLocalHost()}:27000/debug/vars")
      .build()
    val adapter = moshi.adapter<Variables>()
    val variables = okHttpClient.newCall(request).execute().use {
      adapter.fromJson(it.body!!.source())!!
    }
    return variables.QueriesProcessed["SelectScatter"] ?: 0
  }

  /**
   * Digs into the MySQL log to find the last executed DML statement that passed through Vitess.
   */
  private fun extractLastDmlQuery(): String? {
    return connect()?.let { c ->
      c.createStatement().use { s ->
        s.executeQuery(
          """
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
                """.trimIndent()
        )
          .uniqueString()
      }
    }
  }

  companion object {
    private val vtgateKeyspaceIdRegex = "vtgate:: keyspace_id:([^ ]+)".toRegex()
  }
}

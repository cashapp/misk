package misk.jdbc

import com.squareup.moshi.Moshi
import misk.moshi.adapter
import misk.okio.split
import net.ttddyy.dsproxy.ExecutionInfo
import net.ttddyy.dsproxy.QueryInfo
import net.ttddyy.dsproxy.listener.QueryExecutionListener
import net.ttddyy.dsproxy.support.ProxyDataSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import java.io.EOFException
import javax.inject.Singleton
import javax.sql.DataSource

data class Instruction(
  val Opcode: String,
  val Input: Instruction?
) {
  val isScatter: Boolean
    get() {
      return Opcode == "SelectScatter" || Input?.isScatter ?: false
    }
}

data class QueryPlan(
  val Original: String,
  val Instructions: Instruction,
  val ExecCount: Int
) {
  val isScatter: Boolean
    get() = Instructions.isScatter
}

/**
 * Throws a [CrossShardQueryException] for scatter queries that doesn't have a lookup vindex.
 * Note: Current implementation is not thread safe and will not work in production.
 */
@Singleton
class CrossShardQueryDetector(
  val okHttpClient: OkHttpClient,
  val moshi: Moshi,
  val config: DataSourceConfig
) : DataSourceDecorator {

  private val listener = Listener()

  override fun decorate(dataSource: DataSource): DataSource {
    if (config.type != DataSourceType.VITESS) return dataSource

    val proxy = ProxyDataSource(dataSource)
    proxy.addListener(listener)
    return proxy
  }

  inner class Listener : QueryExecutionListener {
    val disabled = ThreadLocal.withInitial { false }
    val count = ThreadLocal.withInitial { 0 }

    override fun beforeQuery(execInfo: ExecutionInfo?, queryInfoList: MutableList<QueryInfo>?) {
      if (disabled.get()) return

      count.set(extractScatterQueryCount())
    }

    override fun afterQuery(execInfo: ExecutionInfo, queryInfoList: MutableList<QueryInfo>) {
      if (disabled.get()) return

      val newScatterQueryCount = extractScatterQueryCount()
      if (newScatterQueryCount > count.get()) {
        throw CrossShardQueryException()
      }
    }

    fun <T> disable(body: () -> T): T = disabled.withValue(true, body)
  }

  private fun extractScatterQueryCount(): Int {
    val request = Request.Builder()
        .url("http://localhost:27000/debug/query_plans")
        .build()
    val response = okHttpClient.newCall(request).execute()
    val source = response.body()!!.source()
    return parseQueryPlans(source).filter { it.isScatter }.sumBy { it.ExecCount }
  }

  private val EMPTY_LINE = "\n\n".encodeUtf8()

  fun parseQueryPlans(data: BufferedSource): Sequence<QueryPlan> {
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

  fun <T> disable(body: () -> T): T  = listener.disable(body)
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

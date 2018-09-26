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
import javax.inject.Inject
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

class CrossShardQueryDetector : DataSourceDecorator {
  @Inject var okHttpClient: OkHttpClient = OkHttpClient()
  @Inject var moshi = Moshi.Builder().build()

  override fun decorate(dataSource: DataSource): DataSource {
    val proxy = ProxyDataSource(dataSource)
    proxy.addListener(object : QueryExecutionListener {
      val count = ThreadLocal.withInitial { 0 }

      override fun beforeQuery(execInfo: ExecutionInfo?, queryInfoList: MutableList<QueryInfo>?) {
        count.set(extractScatterQueryCount())
      }

      override fun afterQuery(execInfo: ExecutionInfo, queryInfoList: MutableList<QueryInfo>) {
        val newScatterQueryCount = extractScatterQueryCount()
        if (newScatterQueryCount > count.get()) {
          throw CrossShardQueryException()
        }
      }
    })
    return proxy
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
}

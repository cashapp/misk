package misk.web

import com.google.inject.util.Modules
import com.squareup.moshi.Moshi
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.moshi.adapter
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.WebAction
import misk.web.jetty.ConnectionMetrics
import misk.web.jetty.JettyConnectionMetricsCollector
import misk.web.jetty.JettyService
import misk.web.jetty.MeasuredQueuedThreadPool
import misk.web.jetty.MeasuredThreadPool
import misk.web.jetty.ThreadPoolMetrics
import misk.web.mediatype.MediaTypes
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.eclipse.jetty.util.thread.QueuedThreadPool
import org.eclipse.jetty.util.thread.ThreadPool
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class JettyServiceMetricsTest {
  @MiskTestModule val module = TestModule()

  @Inject lateinit var jettyService: JettyService
  @Inject lateinit var connectionMetrics: ConnectionMetrics
  @Inject lateinit var connectionMetricsCollector: JettyConnectionMetricsCollector
  @Inject lateinit var moshi: Moshi

  @Test fun connectionMetrics() {
    val httpClient = OkHttpClient()
    val request = Request.Builder()
      .get()
      .header("user-agent", "JettyServiceMetricsTest")
      .url(serverUrlBuilder().encodedPath("/hello").build())
      .build()

    val response = httpClient.newCall(request).execute()
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body?.string()).isEqualTo("hi!")

    connectionMetricsCollector.refreshMetrics()

    val labels = ConnectionMetrics.forPort("http", 0) // It's the configured port not the actual
    assertThat(connectionMetrics.acceptedConnections.labels(*labels).get()).isEqualTo(1.0)
    assertThat(connectionMetrics.activeConnections.labels(*labels).get()).isEqualTo(1.0)
    assertThat(connectionMetrics.bytesReceived.labels(*labels).get()).isEqualTo(130.0)
    assertThat(connectionMetrics.bytesSent.labels(*labels).get()).isEqualTo(141.0)
    assertThat(connectionMetrics.messagesReceived.labels(*labels).get()).isEqualTo(1.0)
    assertThat(connectionMetrics.messagesSent.labels(*labels).get()).isEqualTo(1.0)

    // Force close the okhttp connection
    httpClient.connectionPool.evictAll()

    // Wait for the active connections to go to zero. This is done by another thread (and we can't
    // control it) so we need to do a spin wait on time out
    val timeout = System.currentTimeMillis() + 5000
    while (System.currentTimeMillis() < timeout &&
      connectionMetrics.activeConnections.labels(*labels).get() != 0.0
    ) {
      Thread.sleep(500)
    }

    assertThat(System.currentTimeMillis()).isLessThan(timeout)

    // Active connections should have dropped to zero, all other metrics should remain the same
    assertThat(connectionMetrics.acceptedConnections.labels(*labels).get()).isEqualTo(1.0)
    assertThat(connectionMetrics.activeConnections.labels(*labels).get()).isEqualTo(0.0)
    assertThat(connectionMetrics.bytesReceived.labels(*labels).get()).isEqualTo(130.0)
    assertThat(connectionMetrics.bytesSent.labels(*labels).get()).isEqualTo(141.0)
    assertThat(connectionMetrics.messagesReceived.labels(*labels).get()).isEqualTo(1.0)
    assertThat(connectionMetrics.messagesSent.labels(*labels).get()).isEqualTo(1.0)

    // Refresh metrics, make sure we don't double add
    connectionMetricsCollector.refreshMetrics()
    assertThat(connectionMetrics.acceptedConnections.labels(*labels).get()).isEqualTo(1.0)
    assertThat(connectionMetrics.activeConnections.labels(*labels).get()).isEqualTo(0.0)
    assertThat(connectionMetrics.bytesReceived.labels(*labels).get()).isEqualTo(130.0)
    assertThat(connectionMetrics.bytesSent.labels(*labels).get()).isEqualTo(141.0)
    assertThat(connectionMetrics.messagesReceived.labels(*labels).get()).isEqualTo(1.0)
    assertThat(connectionMetrics.messagesSent.labels(*labels).get()).isEqualTo(1.0)
  }

  @Test fun threadPoolMetrics() {
    val httpClient = OkHttpClient()
    val request = Request.Builder()
      .get()
      .url(serverUrlBuilder().encodedPath("/current-pool-metrics").build())
      .build()

    val adapter = moshi.adapter<PoolMetricsResponse>()
    val response = httpClient.newCall(request).execute()
    assertThat(response.code).isEqualTo(200)

    val metrics = adapter.fromJson(response.body?.string()!!)!!
    assertThat(metrics.queuedJobs).isEqualTo(0.0)
    assertThat(metrics.size).isEqualTo(10.0)
    assertThat(metrics.utilization).isCloseTo(0.5, Offset.offset(0.35))
    assertThat(metrics.utilization_max).isCloseTo(0.5, Offset.offset(0.35))
  }

  internal class HelloAction @Inject constructor() : WebAction {
    @Get("/hello")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun hi(): String {
      return "hi!"
    }
  }

  data class PoolMetricsResponse(
    val queuedJobs: Double,
    val size: Double,
    val utilization: Double,
    val utilization_max: Double
  )

  internal class CurrentPoolMetricsAction @Inject constructor() : WebAction {
    @Inject lateinit var threadPoolMetrics: ThreadPoolMetrics

    @Get("/current-pool-metrics")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun getCurrentPoolMetrics(): PoolMetricsResponse {
      threadPoolMetrics.refresh()

      return PoolMetricsResponse(
        queuedJobs = threadPoolMetrics.queuedJobs.get(),
        size = threadPoolMetrics.size.get(),
        utilization = threadPoolMetrics.utilization.get(),
        utilization_max = threadPoolMetrics.utilizationMax.get()
      )
    }
  }

  internal class TestModule : KAbstractModule() {
    override fun configure() {
      install(
        Modules.override(WebServerTestingModule()).with(
          object : KAbstractModule() {
            override fun configure() {
              val pool = QueuedThreadPool(
                10, 10 // Fixed # of threads
              )
              bind<ThreadPool>().toInstance(pool)
              bind<MeasuredThreadPool>().toInstance(MeasuredQueuedThreadPool(pool))
            }
          }
        )
      )
      install(MiskTestingServiceModule())
      install(WebActionModule.create<HelloAction>())
      install(WebActionModule.create<CurrentPoolMetricsAction>())
    }
  }

  private fun serverUrlBuilder(): HttpUrl.Builder {
    return jettyService.httpServerUrl.newBuilder()
  }
}

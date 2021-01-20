package misk.web

import com.google.inject.util.Modules
import misk.concurrent.ExecutorServiceFactory
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.ClockModule
import misk.time.FakeResourcePool
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Duration
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@MiskTest
internal class DegradedHealthStressTest {
  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var jettyService: JettyService
  @Inject lateinit var fakeResourcePool: FakeResourcePool
  @Inject lateinit var executorServiceFactory: ExecutorServiceFactory

  private lateinit var executorService: ScheduledExecutorService
  private lateinit var httpClient: OkHttpClient

  private val successCount = AtomicInteger()
  private val shedCount = AtomicInteger()
  private val failureCount = AtomicInteger()

  @BeforeEach
  internal fun setUp() {
    executorService = executorServiceFactory.scheduled("DegradedHealthStressTest-%d", 1)
    httpClient = OkHttpClient()
    httpClient.dispatcher.maxRequestsPerHost = 100
    httpClient.dispatcher.maxRequests = 100
  }

  @AfterEach
  fun tearDown() {
    httpClient.dispatcher.executorService.shutdown()
  }

  /**
   * This test is non-deterministic! It attempts to confirm that concurrency load shedding is
   * working with real requests. It should take ~15 seconds to complete.
   *
   * It consumes a resource-constrained endpoint, /work, that takes 200 ms per call and operates on
   * up to 5 calls a time. In total the service can complete 25 calls per second.
   *
   * It's inbound load is double its capacity: it sends 50 calls per second!
   *
   * Once stabilized we expect ~125 calls to complete successfully and the rest to be shed. Requests
   * that are not shed that should have been will time out, and the goal of the concurrency limiter
   * is to minimize such failures.
   */
  @Test
  fun contentionStressTest() {
    // Support 25 calls per second (5 resources are available, and each resource is used for 200ms).
    fakeResourcePool.total = 5

    // Send 50 calls per second.
    val recurringTask = executorService.scheduleAtFixedRate({ makeAsyncHttpCall() },
        0, 1000L / 50, TimeUnit.MILLISECONDS)

    // Make 10 seconds of calls so the concurrency limiter can stabilize.
    Thread.sleep(10_000)
    successCount.set(0)
    shedCount.set(0)
    failureCount.set(0)

    // Observe 5 seconds of data.
    Thread.sleep(5_000)
    recurringTask.cancel(false)

    // Expect 125 successful calls, most of the rest shed, and the remainder failed.
    println("successCount=$successCount, shedCount=$shedCount, failureCount=$failureCount")
    assertThat(successCount.get()).isCloseTo(125, Offset.offset(50))
    assertThat(shedCount.get()).isCloseTo(125, Offset.offset(50))
    assertThat(failureCount.get()).isCloseTo(0, Offset.offset(50))
  }

  class UseConstrainedResourceAction @Inject constructor(
    private val fakeResourcePool: FakeResourcePool
  ) : WebAction {
    @Get("/use_constrained_resource")
    fun get(): String {
      fakeResourcePool.useResource(Duration.ofMillis(50), Duration.ofMillis(200))
      return "success"
    }
  }

  private fun makeAsyncHttpCall() {
    val url = jettyService.httpServerUrl.resolve("/use_constrained_resource")!!
    val request = Request.Builder()
        .url(url)
        .build()

    httpClient.newCall(request).enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        failureCount.incrementAndGet()
      }

      override fun onResponse(call: Call, response: Response) {
        response.use {
          when (response.code) {
            200 -> successCount.incrementAndGet()
            503 -> shedCount.incrementAndGet()
            else -> failureCount.incrementAndGet()
          }
        }
      }
    })
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(Modules.override(WebTestingModule()).with(ClockModule()))
      install(WebActionModule.create<UseConstrainedResourceAction>())
      bind<FakeResourcePool>().asSingleton()
    }
  }
}

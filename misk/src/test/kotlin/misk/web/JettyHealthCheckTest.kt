package misk.web

import com.google.inject.util.Modules
import misk.concurrent.ExecutorServiceFactory
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.jetty.util.thread.ExecutorThreadPool
import org.eclipse.jetty.util.thread.ThreadPool
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.Phaser
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Qualifier

@MiskTest(startService = true)
internal class JettyHealthCheckTest {

  @MiskTestModule val module = TestModule()
  @Inject lateinit var jettyService: JettyService
  @Inject @field:Requests lateinit var requestsPhaser: Phaser
  @Inject @field:Health lateinit var healthPhaser: Phaser
  @Inject lateinit var executorFactory: ExecutorServiceFactory
  @Inject lateinit var threadPool: ThreadPool

  @Test fun health() {
    val executor = executorFactory.unbounded("testing")
    val httpClient = OkHttpClient()
    val request = Request.Builder()
        .get()
        .url(httpUrlBuilder().encodedPath("/block").build())
        .build()
    val healthRequest = Request.Builder()
        .get()
        .url(healthUrlBuilder().encodedPath("/health").build())
        .build()

    // exhaust the thread pool
    val responses = mutableListOf<Future<Response>>()
    // Unfortunately the # of threads vary locally vs CI and it's not clear why, so dynamically compute.
    val parallelRequests = (threadPool as ExecutorThreadPool).maxThreads - threadPool.threads + threadPool.idleThreads
    requestsPhaser.bulkRegister(parallelRequests + 1)
    // Jetty can be flaky on rejecting a request, so ensure we have exhausted the thread pool.
    while (requestsPhaser.arrivedParties < parallelRequests) {
      responses.add(executor.submit(Callable { httpClient.newCall(request).execute() }))
      Thread.sleep(10)
    }
    requestsPhaser.arrive()
    requestsPhaser.awaitAdvanceInterruptibly(0, 5, TimeUnit.SECONDS)

    // verify all threads are busy
    assertThatThrownBy { httpClient.newCall(request).execute() }
        .isInstanceOf(IOException::class.java)

    val healthResponses = mutableListOf<Future<Response>>()
    // make sure we can check liveness and readiness in parallel
    healthPhaser.bulkRegister(2 + 1)
    healthResponses.add(executor.submit(Callable { httpClient.newCall(healthRequest).execute() }))
    healthResponses.add(executor.submit(Callable { httpClient.newCall(healthRequest).execute() }))
    healthPhaser.arriveAndDeregister()

    healthResponses.forEach {
      val r = it.get(5, TimeUnit.SECONDS)
      assertThat(r.code).isEqualTo(200)
      assertThat(r.body?.string()).isEqualTo("healthy")
    }

    // release the blocking threads
    requestsPhaser.arrive()

    // double check these requests were successfully processed
    val successes = responses.mapNotNull {
      try {
        it.get(5, TimeUnit.SECONDS)
      } catch (e: Exception) {
        // Jetty can be flaky on rejecting a request, so just ensure we have the expected #
        // of successful responses, not a specific order.
        null
      }
    }

    successes.forEach {
      assertThat(it.code).isEqualTo(200)
      assertThat(it.body?.string()).isEqualTo("done")
    }
    assertThat(successes).hasSize(parallelRequests)

    executor.shutdown()
  }

  internal class TestModule : KAbstractModule() {
    override fun configure() {
      install(Modules.override(WebTestingModule()).with(
          object : KAbstractModule() {
            override fun configure() {
              val pool = ExecutorThreadPool(ThreadPoolExecutor(
                  // There is some flakiness in Jetty startup when it eagerly creates core threads
                  // and those core threads are not available to be used. Just lazily create for tests.
                  0,
                  10,
                  60, TimeUnit.SECONDS,
                  SynchronousQueue()), 0)
              bind<ThreadPool>().toInstance(pool)
            }
          }
      ))
      install(WebActionModule.create<BlockingAction>())
      install(WebActionModule.create<HealthAction>())
      bind<Phaser>().annotatedWith<Requests>().toInstance(Phaser())
      bind<Phaser>().annotatedWith<Health>().toInstance(Phaser())
    }
  }

  @Qualifier
  internal annotation class Requests

  @Qualifier
  internal annotation class Waiting

  @Qualifier
  internal annotation class HealthBlocking

  @Qualifier
  internal annotation class Health

  internal class BlockingAction @Inject constructor(
    @Requests private val phaser: Phaser
  ) : WebAction {
    @Get("/block")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun block(): String {
      phaser.arrive()
      phaser.awaitAdvanceInterruptibly(0, 5, TimeUnit.SECONDS)
      phaser.arrive()
      phaser.awaitAdvanceInterruptibly(1, 5, TimeUnit.SECONDS)
      return "done"
    }
  }

  internal class HealthAction @Inject constructor(
    @Health private val phaser: Phaser
  ) : WebAction {
    @Get("/health")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun check(): String {
      phaser.arrive()
      phaser.awaitAdvanceInterruptibly(0, 5, TimeUnit.SECONDS)
      return "healthy"
    }
  }

  private fun httpUrlBuilder(): HttpUrl.Builder {
    return jettyService.httpServerUrl.newBuilder()
  }

  private fun healthUrlBuilder(): HttpUrl.Builder {
    return jettyService.healthServerUrl!!.newBuilder()
  }
}

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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Qualifier

@MiskTest(startService = true)
internal class JettyHealthCheckTest {

  companion object {
    const val parallelRequests = 2
  }

  @MiskTestModule val module = TestModule()
  @Inject lateinit var jettyService: JettyService
  @Inject @field:Blocking lateinit var blocking: CountDownLatch
  @Inject @field:Waiting lateinit var waiting: CountDownLatch
  @Inject @field:HealthBlocking lateinit var healthBlocking: CountDownLatch
  @Inject @field:HealthWaiting lateinit var healthWaiting: CountDownLatch
  @Inject lateinit var executorFactory: ExecutorServiceFactory

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
    for (i in 1..parallelRequests) {
      responses.add(executor.submit(Callable { httpClient.newCall(request).execute() }))
    }
    waiting.await(5, TimeUnit.SECONDS)

    // all threads are busy
    assertThatThrownBy { httpClient.newCall(request).execute() }
        .isInstanceOf(IOException::class.java)

    val healthResponses = mutableListOf<Future<Response>>()
    // make sure we can check liveness and readiness in parallel
    healthResponses.add(executor.submit(Callable { httpClient.newCall(healthRequest).execute() }))
    healthResponses.add(executor.submit(Callable { httpClient.newCall(healthRequest).execute() }))
    healthWaiting.await(5, TimeUnit.SECONDS)
    healthBlocking.countDown()

    healthResponses.forEach {
      val r = it.get(5, TimeUnit.SECONDS)
      assertThat(r.code).isEqualTo(200)
      assertThat(r.body?.string()).isEqualTo("healthy")
    }

    // release the blocking threads
    blocking.countDown()

    // double check these requests were successfully processed
    responses.forEach {
      val r = it.get(5, TimeUnit.SECONDS)
      assertThat(r.code).isEqualTo(200)
      assertThat(r.body?.string()).isEqualTo("done")
    }
  }

  internal class TestModule : KAbstractModule() {
    override fun configure() {
      install(Modules.override(WebTestingModule()).with(
          object : KAbstractModule() {
            override fun configure() {
              // jetty reserves 5 threads
              val threadSize = parallelRequests + 5
              val pool = ExecutorThreadPool(
                  ThreadPoolExecutor(threadSize, threadSize, 0, TimeUnit.MILLISECONDS,
                      SynchronousQueue())
              )
              bind<ThreadPool>().toInstance(pool)
            }
          }
      ))
      install(WebActionModule.create<BlockingAction>())
      install(WebActionModule.create<HealthAction>())
      bind<CountDownLatch>().annotatedWith<Blocking>().toInstance(CountDownLatch(1))
      bind<CountDownLatch>().annotatedWith<Waiting>().toInstance(CountDownLatch(parallelRequests))
      bind<CountDownLatch>().annotatedWith<HealthBlocking>().toInstance(CountDownLatch(1))
      bind<CountDownLatch>().annotatedWith<HealthWaiting>().toInstance(CountDownLatch(2))
    }
  }

  @Qualifier
  internal annotation class Blocking

  @Qualifier
  internal annotation class Waiting

  @Qualifier
  internal annotation class HealthBlocking

  @Qualifier
  internal annotation class HealthWaiting

  internal class BlockingAction @Inject constructor(
    @Blocking private val blocking: CountDownLatch,
    @Waiting private val waiting: CountDownLatch
  ) : WebAction {
    @Get("/block")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun block(): String {
      waiting.countDown()
      blocking.await(5, TimeUnit.SECONDS)
      return "done"
    }
  }

  internal class HealthAction @Inject constructor(
    @HealthBlocking private val blocking: CountDownLatch,
    @HealthWaiting private val waiting: CountDownLatch) : WebAction {
    @Get("/health")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun check(): String {
      waiting.countDown()
      blocking.await(5, TimeUnit.SECONDS)
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

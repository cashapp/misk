package misk.web

import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject

@MiskTest
internal class ZeroIdleTimeoutTest : AbstractJettyShutdownTest() {
  @MiskTestModule val module = TestModule(idleTimeout = 0)

  @Test fun `zero idleTimeout will timeout if there's http connection`() {
    shutdownTest(timeoutMs = 2000, timeoutExpected = true)
  }

  @Test fun `zero idleTimeout won't timeout if no http connection`() {
    shutdownTest(timeoutMs = 2000, timeoutExpected = false, makeHttpCall = false)
  }
}

@MiskTest
internal class ZeroIdleTimeoutPositiveShutdownIdleTimeoutTest : AbstractJettyShutdownTest() {
  @MiskTestModule val module = TestModule(idleTimeout = 0, shutdownIdleTimeout = 10)

  @Test fun `zero idleTimeout with positive shutdownIdleTimeout won't timeout`() {
    shutdownTest(timeoutMs = 2000, timeoutExpected = false)
  }
}

@MiskTest
internal class PositiveIdleTimeoutTest : AbstractJettyShutdownTest() {
  @MiskTestModule val module = TestModule(idleTimeout = 10)

  @Test fun `positive idleTimeout won't timeout`() {
    shutdownTest(timeoutMs = 2000, timeoutExpected = false)
  }
}

@MiskTest
internal class PositiveIdleTimeoutZeroShutdownIdleTimeoutTest : AbstractJettyShutdownTest() {
  // idleTimeout is set to a larger value to prevent closing the connection before
  // `jetty.stopAsync()` is invoked
  @MiskTestModule val module = TestModule(idleTimeout = 1000, shutdownIdleTimeout = 0)

  @Test fun `positive idleTimeout with zero shutdownIdleTimeout will timeout`() {
    shutdownTest(timeoutMs = 3000, timeoutExpected = true)
  }
}

internal abstract class AbstractJettyShutdownTest {
  @Inject lateinit var jetty: JettyService

  class HelloAction @Inject constructor() : WebAction {
    @Get("/hello")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun hi(): String = "hi!"
  }

  fun shutdownTest(
    timeoutMs: Long,
    timeoutExpected: Boolean,
    makeHttpCall: Boolean = true
  ) {
    jetty.startAsync()
    jetty.awaitRunning()

    if (makeHttpCall) {
      callHello()
    }

    jetty.stopAsync()
    try {
      jetty.awaitTerminated(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
      assertThat(timeoutExpected).isTrue()
      return
    }
    assertThat(timeoutExpected).isFalse()
  }

  private fun callHello() {
    val httpClient = OkHttpClient()
    val request = Request.Builder()
      .url(jetty.httpServerUrl.resolve("/hello")!!)
      .build()
    val response = httpClient.newCall(request).execute()
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body?.string()).isEqualTo("hi!")
  }

  class TestModule(
    private val idleTimeout: Long,
    private val shutdownIdleTimeout: Long? = null,
  ) : KAbstractModule() {
    override fun configure() {
      install(
        WebServerTestingModule(
          webConfig = WebServerTestingModule.TESTING_WEB_CONFIG.copy(
            idle_timeout = idleTimeout,
            override_shutdown_idle_timeout = shutdownIdleTimeout,
          )
        )
      )
      install(MiskTestingServiceModule())
      install(WebActionModule.create<HelloAction>())
    }
  }
}

package misk.web.resources

import misk.web.NetworkChain
import misk.web.Request
import misk.web.Response
import misk.web.actions.WebAction
import misk.web.toResponseBody
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.reflect.KFunction

class ResourceInterceptorTest {
  // TODO(adrw)

  private val upstreamServer = MockWebServer()
  private lateinit var interceptor : ResourceInterceptor

  @BeforeEach
  internal fun setUp() {
    upstreamServer.start()

    interceptor = ResourceInterceptor(mutableListOf(
        ResourceInterceptor.Entry("/local/prefix/", "/web/local/")
    ))
  }

  @AfterEach
  internal fun tearDown() {
    upstreamServer.shutdown()
  }

  @Test
  internal fun test1() {
    assertThat(true)
  }

  class FakeNetworkChain(
    override val request: Request
  ) : NetworkChain {
    override val action: WebAction
      get() = throw AssertionError()

    override val function: KFunction<*>
      get() = throw AssertionError()

    override fun proceed(request: Request): Response<*> {
      return Response("I am not intercepted".toResponseBody())
    }
  }
}
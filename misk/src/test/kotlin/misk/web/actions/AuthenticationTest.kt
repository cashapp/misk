package misk.web.actions

import com.google.inject.CreationException
import com.google.inject.Guice
import com.google.inject.Stage
import misk.MiskCaller
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientFactory
import misk.security.authz.Authenticated
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.Unauthenticated
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.WebActionModule
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
class AuthenticationTest {
  @MiskTestModule
  val module = TestWebActionModule()

  @Inject private lateinit var jetty: JettyService
  @Inject private lateinit var httpClientFactory: HttpClientFactory

  @Test fun customServiceAccess_unauthenticated() {
    assertThat(executeRequest(path = "/custom_service_access"))
      .isEqualTo("unauthenticated")
  }

  @Test fun customServiceAccess_unauthorized() {
    assertThat(executeRequest(path = "/custom_service_access", service = "customers"))
      .isEqualTo("unauthorized")
  }

  @ParameterizedTest
  @ValueSource(strings = ["payments", "some_other_service"])
  fun customServiceAccess_authorized(service: String) {
    val caller = MiskCaller(service)
    assertThat(executeRequest(path = "/custom_service_access", service))
      .isEqualTo("$caller authorized as custom service")
  }

  @Test fun customCapabilityAccess_unauthenticated() {
    assertThat(executeRequest(path = "/custom_capability_access"))
      .isEqualTo("unauthenticated")
  }

  @Test fun customCapabilityAccess_unauthorized() {
    assertThat(
      executeRequest(
        path = "/custom_capability_access",
        user = "sandy",
        capabilities = "guest"
      )
    )
      .isEqualTo("unauthorized")
  }

  @ParameterizedTest
  @ValueSource(strings = ["admin", "some_other_group"])
  fun customCapabilityAccess_authorized(cap: String) {
    val caller = MiskCaller(user = "sandy", capabilities = setOf(cap))
    assertThat(
      executeRequest(
        path = "/custom_capability_access",
        user = "sandy",
        capabilities = cap
      )
    )
      .isEqualTo("$caller authorized with custom capability")
  }

  private class MixesUnauthenticatedWithOtherAnnotations @Inject constructor() : WebAction {
    @Get("/oops")
    @Unauthenticated
    @Authenticated
    fun get() {}
  }

  @Test
  fun `stacking @Unauthenticated with other access annotations is an error`() {
    val exception = assertFailsWith<CreationException> {
      Guice.createInjector(
        Stage.PRODUCTION,
        TestWebActionModule(),
        WebActionModule.create<MixesUnauthenticatedWithOtherAnnotations>())
    }
    assertThat(exception.cause!!.message).contains(
      "MixesUnauthenticatedWithOtherAnnotations::get() is annotated with @misk.security.authz.Unauthenticated, but also annotated with the following access annotations: @misk.security.authz.Authenticated. This is a contradiction."
    )
  }

  /** Executes a request and returns the response body as a string. */
  private fun executeRequest(
    path: String = "/",
    service: String? = null,
    user: String? = null,
    capabilities: String? = null
  ): String? {
    val client = createOkHttpClient()

    val baseUrl = jetty.httpServerUrl
    val requestBuilder = Request.Builder()
      .url(baseUrl.resolve(path)!!)
    service?.let {
      requestBuilder.header(FakeCallerAuthenticator.SERVICE_HEADER, service)
    }
    user?.let {
      requestBuilder.header(FakeCallerAuthenticator.USER_HEADER, user)
    }
    capabilities?.let {
      requestBuilder.header(FakeCallerAuthenticator.CAPABILITIES_HEADER, capabilities)
    }
    val call = client.newCall(requestBuilder.build())
    val response = call.execute()
    return response.body!!.string()
  }

  private fun createOkHttpClient(): OkHttpClient {
    val config = HttpClientEndpointConfig(jetty.httpServerUrl.toString())
    return httpClientFactory.create(config)
  }
}

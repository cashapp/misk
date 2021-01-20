package misk.web.actions

import misk.MiskCaller
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientFactory
import misk.security.authz.FakeCallerAuthenticator
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
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

  @Test fun customServiceAccess_authorized() {
    val caller = MiskCaller(service = "payments")
    assertThat(executeRequest(path = "/custom_service_access", service = "payments"))
        .isEqualTo("$caller authorized as custom service")
  }

  @Test fun customCapabilityAccess_unauthenticated() {
    assertThat(executeRequest(path = "/custom_capability_access"))
        .isEqualTo("unauthenticated")
  }

  @Test fun customCapabilityAccess_unauthorized() {
    assertThat(executeRequest(path = "/custom_capability_access", user = "sandy", capabilities = "guest"))
        .isEqualTo("unauthorized")
  }

  @Test fun customCapabilityAccess_authorized() {
    val caller = MiskCaller(user = "sandy", capabilities = setOf("admin"))
    assertThat(executeRequest(path = "/custom_capability_access", user = "sandy", capabilities = "admin"))
        .isEqualTo("$caller authorized with custom capability")
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

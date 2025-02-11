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
import jakarta.inject.Inject
import misk.security.authz.AccessInterceptor
import misk.web.toResponseBody
import wisp.logging.LogCollector
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
class AuthenticationTest {
  @MiskTestModule
  val module = TestWebActionModule()

  @Inject private lateinit var jetty: JettyService
  @Inject private lateinit var httpClientFactory: HttpClientFactory
  @Inject private lateinit var logCollector: LogCollector

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
    ).isEqualTo("unauthorized")
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
    ).isEqualTo("$caller authorized with custom capability")
  }

  @Test fun testEmptyAuthenticatedWithUser() {
    val caller = MiskCaller(user = "sandy", capabilities = setOf("nothingfancy"))
    assertThat(
      executeRequest(
        path = "/empty_authorized_access",
        user = "sandy",
        capabilities = "nothingfancy"
      )
    ).isEqualTo("unauthorized")
  }

  @ParameterizedTest
  @ValueSource(strings = ["widgeteer", "web-proxy", "access-proxy"]) // web-proxy and access-proxy are both ExcludeFromAllowAnyService
  fun testEmptyAuthenticatedWithService(service: String) {
    val caller = MiskCaller(service = service)
    assertThat(
      executeRequest(
        path = "/empty_authorized_access",
        service = service
      )
    ).isEqualTo("unauthorized")
  }

  @Test fun testEmptyAuthenticatedUnauthenticated() {
    assertThat(
      executeRequest(
        path = "/empty_authorized_access"
      )
    ).isEqualTo("unauthenticated")
  }

  @Test fun testEmptyAuthenticatedPlusCustomUnauthenticated() {
    assertThat(
      executeRequest(
        path = "/empty_authorized_and_custom_capability_access"
      )
    ).isEqualTo("unauthenticated")
  }

  @Test fun testEmptyAuthenticatedPlusCustomWithWrongCapability() {
    assertThat(
      executeRequest(
        path = "/empty_authorized_and_custom_capability_access",
        user = "sandy",
        capabilities = "nothingfancy"
      )
    ).isEqualTo("unauthorized")
  }

  @Test fun testEmptyAuthenticatedPlusCustomAsService() {
    assertThat(
      executeRequest(
        path = "/empty_authorized_and_custom_capability_access",
        service = "widgeteer",
      )
    ).isEqualTo("unauthorized")
  }

  @Test fun checkAccessInterceptorFactoryLogs() {
    // We don't need to execute a request to check these logs, they're logged on installation of
    // the interceptor on the endpoint.

    assertThat(logCollector.takeEvents(AccessInterceptor::class).map { it.message }).containsExactlyInAnyOrder(
      "Conflicting auth annotations on EmptyAuthenticatedWithCustomAnnototationAccessAction::get(), @Authenticated won't have any effect due to @CustomCapabilityAccess",
      "EmptyAuthenticatedAccessAction::get() has an empty set of allowed services and capabilities. Access will be denied. This method of allowing all services and users has been removed, use explicit boolean parameters allowAnyService or allowAnyUser instead."
    )
  }

  @Test fun testEmptyAuthenticatedPlusCustomWithRightCapability() {
    val caller = MiskCaller(user = "sandy", capabilities = setOf("admin"))
    assertThat(
      executeRequest(
        path = "/empty_authorized_and_custom_capability_access",
        user = "sandy",
        capabilities = "admin"
      )
    ).isEqualTo("$caller authorized with CustomCapabilityAccess")
  }

  @ParameterizedTest
  @ValueSource(strings = ["web-proxy", "access-proxy"]) // web-proxy and access-proxy are both ExcludeFromAllowAnyService
  fun testAllowAnyServiceWithExcludedServices(service: String) {
    assertThat(
      executeRequest(
        path = "/allow_any_service_access",
        service = service
      )
    ).isEqualTo("unauthorized")
  }

  @Test fun testAllowAnyServiceNotExcluded() {
    val service = "widgeteer"
    val caller = MiskCaller(service = service)
    assertThat(
      executeRequest(
        path = "/allow_any_service_access",
        service = service
      )
    ).isEqualTo("$caller authorized as any service")
  }

  @Test fun testAllowAnyServiceWithUser() {
    assertThat(
      executeRequest(
        path = "/allow_any_service_access",
        user = "sandy"
      )
    ).isEqualTo("unauthorized")
  }

  @Test fun testAllowAnyServiceWithAuthenticatedAsExcludedButExplicityAllowedService() {
    val service = "web-proxy"
    val caller = MiskCaller(service = service)
    assertThat(
      executeRequest(
        path = "/allow_any_service_plus_authenticated",
        service = service
      )
    ).isEqualTo("$caller authorized as any service")
  }

  @Test fun testAllowAnyServiceWithAuthenticatedAsExcludedService() {
    val service = "access-proxy"
    assertThat(
      executeRequest(
        path = "/allow_any_service_plus_authenticated",
        service = service
      )
    ).isEqualTo("unauthorized")
  }

  @Test fun testAllowAnyServiceWithAuthenticatedAsAllowedUser() {
    val caller = MiskCaller(user = "sandy", capabilities = setOf("admin"))
    assertThat(
      executeRequest(
        path = "/allow_any_service_plus_authenticated",
        user = caller.user,
        capabilities = caller.capabilities.first()
      )
    ).isEqualTo("$caller authorized as any service")
  }

  @Test fun testAllowAnyServiceWithAuthenticatedAsNotAllowedUser() {
    val caller = MiskCaller(user = "sandy", capabilities = setOf("nothing"))
    assertThat(
      executeRequest(
        path = "/allow_any_service_plus_authenticated",
        user = caller.user,
        capabilities = caller.capabilities.first()
      )
    ).isEqualTo("unauthorized")
  }

  @Test fun testAllowAnyUser() {
    val caller = MiskCaller(user = "sandy")
    assertThat(
      executeRequest(
        path = "/allow_any_user_access",
        user = caller.user
      )
    ).isEqualTo("$caller authorized as any user")
  }

  @Test fun testAllowAnyUserDenyServiceOnly() {
    val caller = MiskCaller(service = "test")
    assertThat(
      executeRequest(
        path = "/allow_any_user_access",
        user = caller.user
      )
    ).isEqualTo("unauthenticated")
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
    assertThat(exception.message).contains(
      "MixesUnauthenticatedWithOtherAnnotations::get() is annotated with @Unauthenticated, but also annotated with the following access annotations: @Authenticated. This is a contradiction."
    )
  }

  @Test
  fun `stacking @Authenticated with other access annotations is an error`() {
    val unauthService = MiskCaller(service = "test")
    assertThat(
      executeRequest(
        path = "/auth-and-custom-capability",
        service = unauthService.service
      )
    ).isEqualTo("unauthorized")

    val authService = MiskCaller(service = "dingo")
    assertThat(
      executeRequest(
        path = "/auth-and-custom-capability",
        service = authService.service
      )
    ).isEqualTo("$authService authorized with custom capability")

    val caller = MiskCaller(user = "bob", capabilities = setOf("admin"))
    assertThat(
      executeRequest(
        path = "/auth-and-custom-capability",
        user = caller.user,
        capabilities = caller.capabilities.first()
      )
    ).isEqualTo("$caller authorized with custom capability")
  }

  /** Executes a request and returns the response body as a string. */
  private fun executeRequest(
    path: String = "/",
    service: String? = null,
    user: String? = null,
    capabilities: String? = null
  ): String {
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

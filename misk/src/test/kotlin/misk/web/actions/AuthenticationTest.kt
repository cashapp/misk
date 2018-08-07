package misk.web.actions

import misk.MiskCaller
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientFactory
import misk.inject.KAbstractModule
import misk.scope.ActionScoped
import misk.security.authz.AccessAnnotation
import misk.security.authz.AccessControlModule
import misk.security.authz.fake.FakeCallerAuthenticator
import misk.security.authz.fake.FakeCallerAuthenticatorModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.WebTestingModule
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class AuthenticationTest {
  @MiskTestModule
  val module = TestModule()

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

  @Test fun customRoleAccess_unauthenticated() {
    assertThat(executeRequest(path = "/custom_role_access"))
        .isEqualTo("unauthenticated")
  }

  @Test fun customRoleAccess_unauthorized() {
    assertThat(executeRequest(path = "/custom_role_access", user = "sandy", roles = "guest"))
        .isEqualTo("unauthorized")
  }

  @Test fun customRoleAccess_authorized() {
    val caller = MiskCaller(user = "sandy", roles = setOf("admin"))
    assertThat(executeRequest(path = "/custom_role_access", user = "sandy", roles = "admin"))
        .isEqualTo("$caller authorized as custom role")
  }

  /** Executes a request and returns the response body as a string. */
  private fun executeRequest(
    path: String = "/",
    service: String? = null,
    user: String? = null,
    roles: String? = null
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
    roles?.let {
      requestBuilder.header(FakeCallerAuthenticator.ROLES_HEADER, roles)
    }
    val call = client.newCall(requestBuilder.build())
    val response = call.execute()
    return response.body()!!.string()
  }

  private fun createOkHttpClient(): OkHttpClient {
    val config = HttpClientEndpointConfig(jetty.httpServerUrl.toString())
    return httpClientFactory.create(config)
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(AccessControlModule())

      multibind<WebActionEntry>().toInstance(
          WebActionEntry<CustomServiceAccessAction>())
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<CustomRoleAccessAction>())

      multibind<AccessAnnotation>().toInstance(
          AccessAnnotation(CustomServiceAccess::class, services = listOf("payments")))
      multibind<AccessAnnotation>().toInstance(
          AccessAnnotation(CustomRoleAccess::class, roles = listOf("admin")))
      install(FakeCallerAuthenticatorModule())
    }
  }

  class CustomServiceAccessAction : WebAction {
    @Inject lateinit var scopedCaller: ActionScoped<MiskCaller?>

    @Get("/custom_service_access")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    @CustomServiceAccess
    fun get() = "${scopedCaller.get()} authorized as custom service".toResponseBody()
  }

  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.FUNCTION)
  annotation class CustomServiceAccess

  class CustomRoleAccessAction : WebAction {
    @Inject lateinit var scopedCaller: ActionScoped<MiskCaller?>

    @Get("/custom_role_access")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    @CustomRoleAccess
    fun get() = "${scopedCaller.get()} authorized as custom role".toResponseBody()
  }

  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.FUNCTION)
  annotation class CustomRoleAccess
}


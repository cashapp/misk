package misk.web.authz

import com.google.inject.Guice
import com.google.inject.Provides
import com.google.inject.name.Names
import misk.MiskCaller
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientModule
import misk.client.HttpClientSSLConfig
import misk.client.HttpClientsConfig
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.moshi.MoshiModule
import misk.scope.ActionScoped
import misk.security.authz.AccessControlModule
import misk.security.authz.Authenticated
import misk.security.authz.PeerServiceClientCertAuthenticator
import misk.security.authz.ProxyUserAuthenticator
import misk.security.authz.Unauthenticated
import misk.security.ssl.CertStoreConfig
import misk.security.ssl.Keystores
import misk.security.ssl.TrustStoreConfig
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.WebTestingModule
import misk.web.WebActionModule
import misk.web.WebSslConfig
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest(startService = true)
internal class AccessControlTest {
  @MiskTestModule
  private val module = TestModule()

  @Inject
  private lateinit var jetty: JettyService

  private lateinit var httpClient: OkHttpClient
  private lateinit var httpsClient: OkHttpClient

  @BeforeEach
  fun init() {
    val clientInjector = Guice.createInjector(MoshiModule(), ClientModule(jetty))
    httpClient = clientInjector.getInstance(Names.named("http"))
    httpsClient = clientInjector.getInstance(Names.named("https"))
  }

  @Test fun allowUnauthenticated() {
    val request = Request.Builder()
        .url(jetty.httpServerUrl.newBuilder().encodedPath("/hello/anyone").build())
        .get()
        .build()

    val response = httpClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()?.string()!!).isEqualTo("hello (you are anonymous)")
  }

  @Test fun canAccessCallerIfAuthenticatedEventIfMethodUnauthenticated() {
    val request = Request.Builder()
        .url(jetty.httpsServerUrl!!.newBuilder().encodedPath("/hello/anyone").build())
        .get()
        .build()

    val response = httpsClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()?.string()!!).isEqualTo("hello (you are Client)")
  }

  @Test fun allowsServiceAuthenticationIfServiceInAllowedSet() {
    val request = Request.Builder()
        .url(jetty.httpsServerUrl!!.newBuilder().encodedPath("/hello/misk-client").build())
        .get()
        .build()

    val response = httpsClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()?.string()!!).isEqualTo("hello peer service Client")
  }

  @Test fun failServiceAuthenticationIfNoCallerProvided() {
    val request = Request.Builder()
        .url(jetty.httpServerUrl.newBuilder().encodedPath("/hello/misk-client").build())
        .get()
        .build()

    val response = httpClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(401)
  }

  @Test fun failServiceAuthenticationIfCallerIsUserOnly() {
    val request = Request.Builder()
        .url(jetty.httpServerUrl.newBuilder().encodedPath("/hello/misk-client").build())
        .addHeader(ProxyUserAuthenticator.HEADER_FORWARDED_USER, "marge")
        .addHeader(ProxyUserAuthenticator.HEADER_FORWARDED_CAPABILITIES, "eng,admin")
        .get()
        .build()

    val response = httpClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(403)
  }

  @Test fun failServiceAuthenticationIfServiceNotInAllowedSet() {
    val request = Request.Builder()
        .url(jetty.httpsServerUrl!!.newBuilder().encodedPath("/hello/not-misk-client").build())
        .get()
        .build()

    val response = httpsClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(403)
  }

  @Test fun allowsUserAuthenticationIfRoleInAllowedSet() {
    val request = Request.Builder()
        .url(jetty.httpServerUrl.newBuilder().encodedPath("/hello/admin").build())
        .addHeader(ProxyUserAuthenticator.HEADER_FORWARDED_USER, "marge")
        .addHeader(ProxyUserAuthenticator.HEADER_FORWARDED_CAPABILITIES, "eng,admin")
        .get()
        .build()

    val response = httpClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()!!.string()).isEqualTo("hello admin marge")
  }

  @Test fun failUserAuthenticationIfNoCallerProvided() {
    val request = Request.Builder()
        .url(jetty.httpServerUrl.newBuilder().encodedPath("/hello/admin").build())
        .get()
        .build()

    val response = httpClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(401)
  }

  @Test fun failUserAuthenticationIfCallerIsServiceOnly() {
    val request = Request.Builder()
        .url(jetty.httpsServerUrl!!.newBuilder().encodedPath("/hello/admin").build())
        .get()
        .build()

    val response = httpsClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(403)
  }

  @Test fun failUserAuthenticationIfRoleNotInAllowedSet() {
    val request = Request.Builder()
        .url(jetty.httpServerUrl.newBuilder().encodedPath("/hello/admin").build())
        .addHeader(ProxyUserAuthenticator.HEADER_FORWARDED_USER, "marge")
        .addHeader(ProxyUserAuthenticator.HEADER_FORWARDED_CAPABILITIES, "eng,operator")
        .get()
        .build()

    val response = httpClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(403)
  }

  /** Action that can be invoked without an authenticated caller */
  class UnauthenticatedAction : WebAction {
    @Inject lateinit var caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>

    @Get("/hello/anyone")
    @Unauthenticated
    fun get(): String = "hello (you are ${caller.get()?.principal ?: "anonymous"})"
  }

  /** Action that can only be invoked by the misk-client service */
  class ClientServiceOnlyAction : WebAction {
    @Inject lateinit var caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>

    @Get("/hello/misk-client")
    @Authenticated(services = ["Client"])
    fun get(): String = "hello peer service ${caller.get()?.service}"
  }

  /** Action that can be invoked by any service _other_ than misk-client */
  class OtherServiceOnlyAction : WebAction {
    @Inject lateinit var caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>

    @Get("/hello/not-misk-client")
    @Authenticated(services = ["not-misk-client"])
    fun get(): String = "hello peer service ${caller.get()?.service}"
  }

  /** Action that can be invoked by an admin user */
  class AdminOnlyAction : WebAction {
    @Inject lateinit var caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>

    @Get("/hello/admin")
    @Authenticated(roles = ["admin"])
    fun get(): String = "hello admin ${caller.get()?.user}"
  }

  class ClientModule(val jetty: JettyService) : KAbstractModule() {
    override fun configure() {
      install(HttpClientModule("https", Names.named("https")))
      install(HttpClientModule("http", Names.named("http")))
    }

    @Provides
    @Singleton
    fun provideHttpClientConfig(): HttpClientsConfig {
      return HttpClientsConfig(
          endpoints = mapOf(
              "https" to HttpClientEndpointConfig(
                  jetty.httpsServerUrl!!.toString(),
                  ssl = HttpClientSSLConfig(
                      cert_store = CertStoreConfig(
                          path = "src/test/resources/ssl/client_cert_key_combo.pem",
                          passphrase = "clientpassword",
                          type = Keystores.TYPE_PEM
                      ),
                      trust_store = TrustStoreConfig(
                          path = "src/test/resources/ssl/server_cert.pem",
                          type = Keystores.TYPE_PEM
                      )
                  )),
              "http" to HttpClientEndpointConfig(jetty.httpServerUrl.toString())
          ))

    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(AccessControlModule(PeerServiceClientCertAuthenticator::class, ProxyUserAuthenticator::class))
      install(WebActionModule.create<UnauthenticatedAction>())
      install(WebActionModule.create<ClientServiceOnlyAction>())
      install(WebActionModule.create<OtherServiceOnlyAction>())
      install(WebActionModule.create<AdminOnlyAction>())
      install(WebTestingModule(
          ssl = WebSslConfig(0,
              cert_store = CertStoreConfig(
                  path = "src/test/resources/ssl/server_cert_key_combo.pem",
                  passphrase = "serverpassword",
                  type = Keystores.TYPE_PEM
              ),
              trust_store = TrustStoreConfig(
                  path = "src/test/resources/ssl/client_cert.pem",
                  type = Keystores.TYPE_PEM
              ),
              mutual_auth = WebSslConfig.MutualAuth.REQUIRED)
      ))
    }
  }
}
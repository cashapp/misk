package misk.web.ssl

import com.google.inject.Guice
import com.google.inject.Provides
import com.google.inject.name.Names
import com.google.inject.util.Modules
import helpers.protos.Dinosaur
import misk.MiskModule
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientModule
import misk.client.HttpClientSSLConfig
import misk.client.HttpClientsConfig
import misk.client.ProtoMessageHttpClient
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.moshi.MoshiModule
import misk.scope.ActionScoped
import misk.security.ssl.ClientCertSubject
import misk.security.ssl.KeystoreConfig
import misk.security.ssl.Keystores
import misk.security.x509.X500Name
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestWebModule
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebModule
import misk.web.WebSslConfig
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLHandshakeException

@MiskTest(startService = true)
internal class SslClientServerTest {
  @MiskTestModule
  val module = Modules.combine(
      MiskModule(),
      WebModule(),
      TestWebModule(
          ssl = WebSslConfig(0,
              keystore = KeystoreConfig(
                  path = "src/test/resources/ssl/server_keystore.jceks",
                  passphrase = "serverpassword"
              ),
              truststore = KeystoreConfig(
                  path = "src/test/resources/ssl/client_cert.pem",
                  type = Keystores.TYPE_PEM
              ),
              mutual_auth = WebSslConfig.MutualAuth.REQUIRED)),
      TestModule())

  @Inject
  private lateinit var jetty: JettyService

  private lateinit var noCertClient: ProtoMessageHttpClient
  private lateinit var certClient: ProtoMessageHttpClient
  private lateinit var noTrustClient: ProtoMessageHttpClient

  @BeforeEach
  fun createClient() {
    val clientInjector = Guice.createInjector(MoshiModule(), ClientModule(jetty))
    certClient = clientInjector.getInstance(Names.named("cert-and-trust"))
    noCertClient = clientInjector.getInstance(Names.named("no-cert"))
    noTrustClient = clientInjector.getInstance(Names.named("no-trust"))
  }

  @Test
  fun usesSsl() {
    val dinosaur = certClient.post<Dinosaur>("/hello", Dinosaur.Builder().name("trex").build())
    assertThat(dinosaur.name).isEqualTo("hello trex from misk-client")
  }

  @Test
  fun failsIfNoCertIsPresented() {
    // In this case the server just hangs up, which might result in a handshake exception
    // or a socket exception depending on the state of things at the time of hangup
    assertThrows(Throwable::class.java) {
      noCertClient.post<Dinosaur>("/hello", Dinosaur.Builder().name("trex").build())
    }
  }

  @Test
  fun failsIfServerIsUntrusted() {
    assertThrows(SSLHandshakeException::class.java) {
      noTrustClient.post<Dinosaur>("/hello", Dinosaur.Builder().name("trex").build())
    }
  }

  class HelloAction : WebAction {
    @Inject @ClientCertSubject private lateinit var clientCertSubjectDN: ActionScoped<X500Name?>

    @Post("/hello")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun sayHello(@RequestBody request: Dinosaur):
        Dinosaur = request.newBuilder()
        .name("hello ${request.name} from ${clientCertSubjectDN.get()?.commonName}").build()
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebActionModule.create<HelloAction>())
    }
  }

  // NB(mmihic): The server doesn't get a port until after it starts, so we
  // need to create the client module _after_ we start the services
  class ClientModule(val jetty: JettyService) : KAbstractModule() {
    override fun configure() {
      install(HttpClientModule("cert-and-trust", Names.named("cert-and-trust")))
      install(HttpClientModule("no-cert", Names.named("no-cert")))
      install(HttpClientModule("no-trust", Names.named("no-trust")))
    }

    @Provides
    @Singleton
    fun provideHttpClientConfig(): HttpClientsConfig {
      return HttpClientsConfig(
          endpoints = mapOf(
              "cert-and-trust" to HttpClientEndpointConfig(
                  jetty.httpsServerUrl!!.toString(),
                  ssl = HttpClientSSLConfig(
                      keystore = KeystoreConfig(
                          path = "src/test/resources/ssl/client_keystore.jceks",
                          passphrase = "clientpassword"
                      ),
                      truststore = KeystoreConfig(
                          path = "src/test/resources/ssl/server_cert.pem",
                          type = Keystores.TYPE_PEM
                      )
                  )),
              "no-cert" to HttpClientEndpointConfig(
                  jetty.httpsServerUrl!!.toString(),
                  ssl = HttpClientSSLConfig(
                      keystore = null,
                      truststore = KeystoreConfig(
                          path = "src/test/resources/ssl/server_cert.pem",
                          type = Keystores.TYPE_PEM
                      )
                  )),
              "no-trust" to HttpClientEndpointConfig(
                  jetty.httpsServerUrl!!.toString(),
                  ssl = HttpClientSSLConfig(
                      keystore = KeystoreConfig(
                          path = "src/test/resources/ssl/client_keystore.jceks",
                          passphrase = "clientpassword"
                      ),
                      truststore = KeystoreConfig(
                          path = "src/test/resources/ssl/client_cert.pem",
                          type = Keystores.TYPE_PEM
                      )
                  ))
          ))
    }
  }
}

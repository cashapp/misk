package misk.client

import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.Provides
import com.google.inject.TypeLiteral
import helpers.protos.Dinosaur
import misk.MiskTestingServiceModule
import misk.clustering.Cluster
import misk.clustering.ClusterWatch
import misk.clustering.fake.ExplicitClusterResourceMapper
import misk.config.AppName
import misk.inject.KAbstractModule
import misk.security.ssl.CertStoreConfig
import misk.security.ssl.SslLoader
import misk.security.ssl.TrustStoreConfig
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebConfig
import misk.web.WebSslConfig
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest
internal class TypedPeerHttpClientTest {
  @MiskTestModule val module = TestModule()

  @Inject private lateinit var jetty: JettyService

  @Inject private lateinit var cluster: Cluster

  private lateinit var clientInjector: Injector

  @BeforeEach
  fun createClient() {
    clientInjector = Guice.createInjector(ClientModule(jetty))
  }

  @Test
  fun useTypedClient() {
    val clientFactory = clientInjector.getInstance(
        Key.get(object : TypeLiteral<TypedPeerClientFactory<ReturnADinosaur>>() {}))

    val snapshot = cluster.snapshot
    val client: ReturnADinosaur = clientFactory.client(snapshot.self)

    val response = client.getDinosaur(Dinosaur.Builder().name("trex").build()).execute()
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()).isNotNull()
    assertThat(response.body()?.name!!).isEqualTo("supertrex")
  }

  interface ReturnADinosaur {
    @POST("/cooldinos")
    fun getDinosaur(@Body request: Dinosaur): Call<Dinosaur>
  }

  class ReturnADinosaurAction @Inject constructor() : WebAction {
    @Post("/cooldinos")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun getDinosaur(@RequestBody request: Dinosaur):
        Dinosaur = request.newBuilder().name("super${request.name}").build()
  }

  class FakeCluster @Inject constructor(
    memberIp: String = "127.0.0.1"
  ) : Cluster {

    override val snapshot: Cluster.Snapshot

    init {
      val self = Cluster.Member(name = "self-name", ipAddress = memberIp)
      val resourceMapper = ExplicitClusterResourceMapper()

      resourceMapper.setDefaultMapping(self)

      snapshot = Cluster.Snapshot(
          self = self,
          readyMembers = setOf(),
          selfReady = true,
          resourceMapper = resourceMapper
      )
    }

    override fun watch(watch: ClusterWatch) {
      throw UnsupportedOperationException()
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      // Run a server using a cert that has OU of "Server"
      install(WebTestingModule(WebConfig(
          port = 0,
          idle_timeout = 500000,
          host = "127.0.0.1",
          ssl = WebSslConfig(0,
              cert_store = CertStoreConfig(
                  resource = "classpath:/ssl/server_cert_key_combo.pem",
                  passphrase = "serverpassword",
                  format = SslLoader.FORMAT_PEM
              ),
              trust_store = TrustStoreConfig(
                  resource = "classpath:/ssl/client_cert.pem",
                  format = SslLoader.FORMAT_PEM
              ),
              mutual_auth = WebSslConfig.MutualAuth.REQUIRED)
      )))

      bind<Cluster>().toInstance(FakeCluster())
      install(WebActionModule.create<ReturnADinosaurAction>())
    }
  }

  inner class ClientModule(val jetty: JettyService) : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(TypedPeerHttpClientModule.create<ReturnADinosaur>("dinosaur"))

      bind<JettyService>().toInstance(jetty)

      install(JettyPortPeerClientModule())
    }

    // "Server" is the OU of the cert used by the test server
    @Provides @AppName fun appName() = "Server"

    @Provides
    @Singleton
    fun provideHttpClientConfig(): HttpClientsConfig {
      return HttpClientsConfig(
          endpoints = mapOf(
              "Server" to HttpClientEndpointConfig(
                  url = jetty.httpsServerUrl!!.toString(),
                  clientConfig = HttpClientConfig(
                      ssl = HttpClientSSLConfig(
                          cert_store = CertStoreConfig(
                              resource = "classpath:/ssl/client_cert_key_combo.pem",
                              passphrase = "clientpassword",
                              format = SslLoader.FORMAT_PEM
                          ),
                          trust_store = TrustStoreConfig(
                              resource = "classpath:/ssl/server_cert.pem",
                              format = SslLoader.FORMAT_PEM
                          )
                      )
                  )
              )
          ))
    }
  }
}

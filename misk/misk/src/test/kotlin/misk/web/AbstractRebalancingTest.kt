package misk.web

import misk.inject.KAbstractModule
import misk.security.ssl.CertStoreConfig
import misk.security.ssl.SslLoader
import misk.security.ssl.TrustStoreConfig
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

abstract class AbstractRebalancingTest(
  val percent: Double
) {
  @MiskTestModule
  val module = TestModule()

  @Inject
  lateinit var jettyService: JettyService

  @Test
  fun rebalancing() {
    val httpClient = OkHttpClient()
    val request = Request.Builder()
        .url(jettyService.httpServerUrl)
        .build()

    val response = httpClient.newCall(request).execute()
    checkResponse(response)
  }

  abstract fun checkResponse(response: Response)

  inner class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule(webConfig = WebConfig(
          port = 0,
          idle_timeout = 500000,
          host = "127.0.0.1",
          close_connection_percent = percent,
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
    }
  }
}

@MiskTest(startService = true)
class RebalancingEnabledTest : AbstractRebalancingTest(100.0) {
  override fun checkResponse(response: Response) {
    assertThat(response.header("Connection")).isEqualTo("close")
  }
}

@MiskTest(startService = true)
class RebalancingDisabledTest : AbstractRebalancingTest(0.0) {
  override fun checkResponse(response: Response) {
    assertThat(response.header("Connection")).isNull()
  }
}
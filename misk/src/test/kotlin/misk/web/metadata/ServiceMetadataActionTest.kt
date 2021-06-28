package misk.web.metadata

import com.squareup.moshi.Moshi
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientFactory
import misk.moshi.adapter
import misk.security.authz.FakeCallerAuthenticator
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class ServiceMetadataActionTest {
  @MiskTestModule
  val module = MetadataTestingModule()

  @Inject private lateinit var jetty: JettyService
  @Inject private lateinit var httpClientFactory: HttpClientFactory
  @Inject private lateinit var moshi: Moshi

  @Test fun `service metadata environment uses correct case mapping`() {
    val response = executeRequest()
    assertThat(response.serviceMetadata.environment).isUpperCase
  }

  private fun executeRequest(
    path: String = "/api/service/metadata",
    service: String? = null,
    user: String? = null,
    capabilities: String? = null
  ): ServiceMetadataAction.Response {
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

    val responseAdaptor = moshi.adapter<ServiceMetadataAction.Response>()
    return responseAdaptor.fromJson(response.body!!.source())!!
  }

  private fun createOkHttpClient(): OkHttpClient {
    val config = HttpClientEndpointConfig(jetty.httpServerUrl.toString())
    return httpClientFactory.create(config)
  }
}

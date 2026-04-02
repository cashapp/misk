package misk.web

import com.google.inject.Guice
import com.squareup.moshi.Moshi
import com.squareup.protos.test.parsing.Shipment
import com.squareup.protos.test.parsing.Warehouse
import com.squareup.wire.GrpcCall
import com.squareup.wire.GrpcClient
import com.squareup.wire.GrpcMethod
import com.squareup.wire.Service
import com.squareup.wire.WireRpc
import misk.MiskTestingServiceModule
import misk.grpc.Http2ClientTestingModule
import misk.inject.KAbstractModule
import misk.security.authz.Unauthenticated
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import jakarta.inject.Inject

/**
 * Test that @[EnableUnframedRequests] enables protobuf POST on gRPC endpoints.
 */
@MiskTest(startService = true)
internal class EnableUnframedRequestsTest {
  @MiskTestModule
  val module = TestModule()

  @Inject
  lateinit var moshi: Moshi

  @Inject
  lateinit var jettyService: JettyService

  private lateinit var httpClient: OkHttpClient

  @BeforeEach
  fun createClient() {
    val clientInjector = Guice.createInjector(Http2ClientTestingModule(jettyService))
    httpClient = clientInjector.getInstance(OkHttpClient::class.java)
  }

  @Test
  fun `protobuf POST to unframed grpc endpoint`() {
    val requestBody = Shipment.Builder()
      .shipment_token("abc")
      .build()
    val expectedResponseBody = Warehouse.Builder()
      .warehouse_token("abc")
      .build()

    val request = Request.Builder()
      .post(
        ByteString.of(*requestBody.encode()).toRequestBody(
          MediaTypes.APPLICATION_PROTOBUF_MEDIA_TYPE
        )
      )
      .url(serverUrlBuilder().encodedPath("/test/UnframedGetDestinationWarehouse").build())
      .build()

    val response = httpClient.newCall(request).execute()
    response.use {
      assertThat(response.code).isEqualTo(200)
      val responseBody = Warehouse.ADAPTER.decode(response.body!!.source())
      assertThat(responseBody).isEqualTo(expectedResponseBody)
      assertThat(response.body!!.contentType())
        .isEqualTo(MediaTypes.APPLICATION_PROTOBUF_MEDIA_TYPE)
    }
  }

  @Test
  fun `json POST to unframed grpc endpoint`() {
    val requestBody = Shipment.Builder()
      .shipment_token("abc")
      .build()
    val expectedResponseBody = Warehouse.Builder()
      .warehouse_token("abc")
      .build()

    val request = Request.Builder()
      .post(
        moshi.adapter(Shipment::class.java).toJson(requestBody)
          .toRequestBody(MediaTypes.APPLICATION_JSON_MEDIA_TYPE)
      )
      .url(serverUrlBuilder().encodedPath("/test/UnframedGetDestinationWarehouse").build())
      .build()

    val response = httpClient.newCall(request).execute()
    response.use {
      assertThat(response.code).isEqualTo(200)
      val responseBody = moshi.adapter(Warehouse::class.java).fromJson(response.body!!.source())
      assertThat(responseBody).isEqualTo(expectedResponseBody)
      assertThat(response.body!!.contentType().toString())
        .isEqualTo("application/json;charset=utf-8")
    }
  }

  @Test
  fun `grpc to unframed grpc endpoint`() {
    val requestBody = Shipment.Builder()
      .shipment_token("abc")
      .build()
    val expectedResponseBody = Warehouse.Builder()
      .warehouse_token("abc")
      .build()

    val grpcClient = GrpcClient.Builder()
      .baseUrl(jettyService.httpsServerUrl!!)
      .client(httpClient)
      .build()
    val shippingClient = UnframedShippingClient(grpcClient)

    val responseBody = shippingClient.GetDestinationWarehouse().executeBlocking(requestBody)
    assertThat(responseBody).isEqualTo(expectedResponseBody)
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(
        WebServerTestingModule(
          webConfig = WebServerTestingModule.TESTING_WEB_CONFIG
        )
      )
      install(MiskTestingServiceModule())
      install(WebActionModule.create<UnframedGrpcAction>())
    }
  }

  @Suppress("TestFunctionName")
  class UnframedGrpcAction @Inject constructor() :
    UnframedShippingServer, WebAction {
    @Unauthenticated
    override fun GetDestinationWarehouse(shipment: Shipment): Warehouse {
      return Warehouse.Builder()
        .warehouse_token(shipment.shipment_token)
        .build()
    }
  }

  @Suppress("TestFunctionName")
  interface UnframedShippingServer : Service {
    @WireRpc(
      path = "/test/UnframedGetDestinationWarehouse",
      requestAdapter = "com.squareup.protos.test.parsing.Shipment#ADAPTER",
      responseAdapter = "com.squareup.protos.test.parsing.Warehouse#ADAPTER"
    )
    @EnableUnframedRequests
    fun GetDestinationWarehouse(shipment: Shipment): Warehouse
  }

  @Suppress("TestFunctionName")
  class UnframedShippingClient(private val client: GrpcClient) : Service {
    @WireRpc(
      path = "/test/UnframedGetDestinationWarehouse",
      requestAdapter = "com.squareup.protos.test.parsing.Shipment#ADAPTER",
      responseAdapter = "com.squareup.protos.test.parsing.Warehouse#ADAPTER"
    )
    fun GetDestinationWarehouse(): GrpcCall<Shipment, Warehouse> = client.newCall(
      GrpcMethod(
        path = "/test/UnframedGetDestinationWarehouse",
        requestAdapter = Shipment.ADAPTER,
        responseAdapter = Warehouse.ADAPTER
      )
    )
  }

  private fun serverUrlBuilder(): HttpUrl.Builder {
    return jettyService.httpsServerUrl!!.newBuilder()
  }
}

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
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import javax.inject.Inject

/**
 * Test that we can send JSON to proto and gRPC endpoints.
 */
@Disabled("gRPC tests are flaky, see https://github.com/cashapp/misk/issues/1853")
@MiskTest(startService = true)
internal class JsonForProtoEndpointsTest {
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
  fun `json to protobuf endpoint`() {
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
      .url(serverUrlBuilder().encodedPath("/get_destination_warehouse").build())
      .build()

    val response = httpClient.newCall(request).execute()
    response.use {
      val responseBody = moshi.adapter(Warehouse::class.java).fromJson(response.body!!.source())
      assertThat(responseBody).isEqualTo(expectedResponseBody)
      assertThat(response.body!!.contentType().toString())
        .isEqualTo("application/json;charset=utf-8")
    }
  }

  @Test
  fun `json to grpc endpoint`() {
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
      .url(serverUrlBuilder().encodedPath("/test/GetDestinationWarehouse").build())
      .build()

    val response = httpClient.newCall(request).execute()
    response.use {
      val responseBody = moshi.adapter(Warehouse::class.java).fromJson(response.body!!.source())
      assertThat(responseBody).isEqualTo(expectedResponseBody)
      assertThat(response.body!!.contentType().toString())
        .isEqualTo("application/json;charset=utf-8")
    }
  }

  @Test
  fun `protobuf to protobuf endpoint`() {
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
      .url(serverUrlBuilder().encodedPath("/get_destination_warehouse").build())
      .build()

    val response = httpClient.newCall(request).execute()
    response.use {
      val responseBody = Warehouse.ADAPTER.decode(response.body!!.source())
      assertThat(responseBody).isEqualTo(expectedResponseBody)
      assertThat(response.body!!.contentType())
        .isEqualTo(MediaTypes.APPLICATION_PROTOBUF_MEDIA_TYPE)
    }
  }

  @Test
  fun `grpc to grpc endpoint`() {
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
    val shippingClient = GrpcShippingClient(grpcClient)

    val responseBody = shippingClient.GetDestinationWarehouse().executeBlocking(requestBody)
    assertThat(responseBody).isEqualTo(expectedResponseBody)
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(
        WebServerTestingModule(
          webConfig = WebServerTestingModule.TESTING_WEB_CONFIG.copy(
            http2 = true
          )
        )
      )
      install(MiskTestingServiceModule())
      install(WebActionModule.create<ProtoEchoShipmentToken>())
      install(WebActionModule.create<GrpcEchoShipmentToken>())
    }
  }

  class ProtoEchoShipmentToken @Inject constructor() : WebAction {
    @Post("/get_destination_warehouse")
    @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
    @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
    fun getDestinationWarehouse(@RequestBody shipment: Shipment) =
      Warehouse.Builder()
        .warehouse_token(shipment.shipment_token)
        .build()
  }

  class GrpcEchoShipmentToken @Inject constructor() :
    ShippingGetDestinationWarehouseBlockingServer, WebAction {
    @Unauthenticated
    override fun GetDestinationWarehouse(shipment: Shipment): Warehouse {
      return Warehouse.Builder()
        .warehouse_token(shipment.shipment_token)
        .build()
    }
  }

  // TODO(jwilson): get Wire to generate this interface.
  interface ShippingGetDestinationWarehouseBlockingServer : Service {
    @WireRpc(
      path = "/test/GetDestinationWarehouse",
      requestAdapter = "com.squareup.protos.test.parsing.Shipment#ADAPTER",
      responseAdapter = "com.squareup.protos.test.parsing.Warehouse#ADAPTER"
    )
    fun GetDestinationWarehouse(shipment: Shipment): Warehouse
  }

  // TODO(jwilson): get Wire to generate this interface.
  class GrpcShippingClient(private val client: GrpcClient) : Service {
    @WireRpc(
      path = "/test/GetDestinationWarehouse",
      requestAdapter = "com.squareup.protos.test.parsing.Shipment#ADAPTER",
      responseAdapter = "com.squareup.protos.test.parsing.Warehouse#ADAPTER"
    )
    fun GetDestinationWarehouse(): GrpcCall<Shipment, Warehouse> = client.newCall(
      GrpcMethod(
        path = "/test/GetDestinationWarehouse",
        requestAdapter = Shipment.ADAPTER,
        responseAdapter = Warehouse.ADAPTER
      )
    )
  }

  private fun serverUrlBuilder(): HttpUrl.Builder {
    return jettyService.httpsServerUrl!!.newBuilder()
  }
}

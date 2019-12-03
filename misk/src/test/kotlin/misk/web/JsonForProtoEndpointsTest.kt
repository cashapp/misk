package misk.web

import com.squareup.moshi.Moshi
import com.squareup.protos.test.parsing.Shipment
import com.squareup.protos.test.parsing.Warehouse
import misk.inject.KAbstractModule
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
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class JsonForProtoEndpointsTest {
  @MiskTestModule
  val module = TestModule()

  @Inject
  lateinit var moshi: Moshi

  @Inject
  lateinit var jettyService: JettyService

  @Test
  fun json() {
    val httpClient = OkHttpClient()
    val requestBody = Shipment.Builder()
        .shipment_token("abc")
        .build()
    val expectedResponseBody = Warehouse.Builder()
        .warehouse_token("abc")
        .build()

    val request = Request.Builder()
        .post(moshi.adapter(Shipment::class.java).toJson(requestBody)
            .toRequestBody(MediaTypes.APPLICATION_JSON_MEDIA_TYPE))
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
  fun protobuf() {
    val requestBody = Shipment.Builder()
        .shipment_token("abc")
        .build()
    val expectedResponseBody = Warehouse.Builder()
        .warehouse_token("abc")
        .build()

    val httpClient = OkHttpClient()
    val request = Request.Builder()
        .post(ByteString.of(*requestBody.encode()).toRequestBody(
            MediaTypes.APPLICATION_PROTOBUF_MEDIA_TYPE))
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

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebActionModule.create<EchoShipmentToken>())
    }
  }

  class EchoShipmentToken @Inject constructor() : WebAction {
    @Post("/get_destination_warehouse")
    @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
    @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
    fun getDestinationWarehouse(@RequestBody shipment: Shipment) =
        Warehouse.Builder()
            .warehouse_token(shipment.shipment_token)
            .build()
  }

  private fun serverUrlBuilder(): HttpUrl.Builder {
    return jettyService.httpServerUrl.newBuilder()
  }
}

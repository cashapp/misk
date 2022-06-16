package misk.web

import com.squareup.moshi.Moshi
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.internal.EMPTY_REQUEST
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class GzipTest : AbstractGzipTest() {
  @MiskTestModule val module = TestModule(gzip = true)

  @Test fun `GET gzip get response body`() {
    get("/miskhype/16").assertGzipEncoding(gzipped = true)
    get("/miskhype/8").assertGzipEncoding(gzipped = false)
  }

  @Test fun `POST gzip response body`() {
    post("/miskhype/16").assertGzipEncoding(gzipped = true)
    post("/miskhype/8").assertGzipEncoding(gzipped = false)
  }
}

@MiskTest(startService = true)
internal class GzipDisabledTest : AbstractGzipTest() {
  @MiskTestModule val module = TestModule(gzip = false)

  @Test fun `GET gzip get response body`() {
    get("/miskhype/16").assertGzipEncoding(gzipped = false)
    get("/miskhype/8").assertGzipEncoding(gzipped = false)
  }

  @Test fun `POST gzip response body`() {
    post("/miskhype/16").assertGzipEncoding(gzipped = false)
    post("/miskhype/8").assertGzipEncoding(gzipped = false)
  }
}

abstract class AbstractGzipTest {
  @Inject lateinit var jetty: JettyService
  @Inject lateinit var moshi: Moshi

  @Test fun `POST unspecified request body`() {
    val response = call(
      Request.Builder()
        .url(jetty.httpServerUrl.resolve("/count")!!)
        .post(miskHypeJson(false, 16))
    )

    assertThat(response.body!!.string()).isEqualTo("16")
  }

  @Test fun `POST identity request body`() {
    val response = call(
      Request.Builder()
        .url(jetty.httpServerUrl.resolve("/count")!!)
        .header("Content-Encoding", "identity")
        .post(miskHypeJson(false, 16))
    )

    assertThat(response.body!!.string()).isEqualTo("16")
  }

  /** Gzip requests are always enabled, even if the gzip responses are not. */
  @Test fun `POST gzip request body`() {
    val response = call(
      Request.Builder()
        .url(jetty.httpServerUrl.resolve("/count")!!)
        .header("Content-Encoding", "gzip")
        .post(miskHypeJson(true, 16))
    )
    assertThat(response.body!!.string()).isEqualTo("16")
  }

  class MiskHypeGetAction @Inject constructor() : WebAction {
    @Get("/miskhype/{times}")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun getMiskHype(@PathParam times: Int) = miskHype(times)
  }

  class MiskHypePostAction @Inject constructor() : WebAction {
    @Post("/miskhype/{times}")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun postMiskHype(@PathParam times: Int) = miskHype(times)
  }

  class CountHypeAction @Inject constructor() : WebAction {
    @Post("/count")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun postMiskHype(@RequestBody request: List<String>): Int = request.count { it == "miskhype" }
  }

  fun get(path: String): Response = call(
    Request.Builder()
      .url(jetty.httpServerUrl.newBuilder().encodedPath(path).build())
  )

  fun post(path: String): Response = call(
    Request.Builder()
      .url(jetty.httpServerUrl.newBuilder().encodedPath(path).build())
      .post(EMPTY_REQUEST)
  )

  fun call(request: Request.Builder): Response {
    val httpClient = OkHttpClient()
    val response = httpClient.newCall(request.build()).execute()
    assertThat(response.code).isEqualTo(200)
    return response
  }

  fun Response.assertGzipEncoding(gzipped: Boolean) {
    use {
      assertThat(networkResponse?.headers?.get("Content-Encoding") == "gzip").isEqualTo(gzipped)
    }
  }

  /** Returns a JSON document like `["miskhype", "miskhype"]`. */
  fun miskHypeJson(gzip: Boolean, count: Int): okhttp3.RequestBody {
    val buffer = Buffer()

    val sink = when {
      gzip -> GzipSink(buffer).buffer()
      else -> buffer
    }

    sink.use {
      sink.writeUtf8("[\"")
      sink.writeUtf8(miskHype(count).joinToString(separator = "\", \""))
      sink.writeUtf8("\"]")
    }

    return buffer.readByteString().toRequestBody(MediaTypes.APPLICATION_JSON_MEDIA_TYPE)
  }

  class TestModule(private val gzip: Boolean) : KAbstractModule() {
    override fun configure() {
      install(
        WebServerTestingModule(
          webConfig = WebServerTestingModule.TESTING_WEB_CONFIG.copy(
            gzip = gzip,
            minGzipSize = 128
          )
        )
      )
      install(MiskTestingServiceModule())
      install(WebActionModule.create<MiskHypeGetAction>())
      install(WebActionModule.create<MiskHypePostAction>())
      install(WebActionModule.create<CountHypeAction>())
    }
  }

  companion object {
    fun miskHype(times: Int): List<String> {
      val list = mutableListOf<String>()
      for (i in 0 until times) {
        list.add("miskhype")
      }
      return list
    }
  }
}

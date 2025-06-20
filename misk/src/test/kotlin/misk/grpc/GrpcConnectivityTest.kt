package misk.grpc

import com.google.inject.Guice
import com.squareup.protos.test.grpc.HelloReply
import com.squareup.protos.test.grpc.HelloRequest
import com.squareup.wire.GrpcStatus
import com.squareup.wire.Service
import com.squareup.wire.WireRpc
import misk.MiskTestingServiceModule
import misk.exceptions.BadRequestException
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.InterruptedIOException
import java.time.Duration
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * This test gets Misk running as a GRPC server and then acts as a basic GRPC client to send a
 * request. It's intended to be interoperable with with the
 * [GRPC 'hello world' sample](https://github.com/grpc/grpc-java/tree/master/examples).
 *
 * That sample includes a client and a server that connect to each other. You can also connect this
 * test's client to that sample server, or that sample client to this test's server.
 */
@Disabled("gRPC tests are flaky in CI, see https://github.com/cashapp/misk/issues/1853")
@MiskTest(startService = true)
class GrpcConnectivityTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var jetty: JettyService
  @Inject private lateinit var helloRpcAction: HelloRpcAction

  private lateinit var client: OkHttpClient

  @BeforeEach
  fun createClient() {
    val clientInjector = Guice.createInjector(Http2ClientTestingModule(jetty))
    client = clientInjector.getInstance(OkHttpClient::class.java)
  }
  
  @ParameterizedTest
  @MethodSource("provideMediaTypes")
  fun happyPath(mediaType: MediaType) {
    val request = Request.Builder()
      .url(jetty.httpsServerUrl!!.resolve("/helloworld.Greeter/SayHello")!!)
      .addHeader("grpc-trace-bin", "")
      .addHeader("grpc-accept-encoding", "gzip")
      .addHeader("grpc-encoding", "gzip")
      .post(object : RequestBody() {
        override fun contentType(): MediaType? {
          return mediaType
        }

        override fun writeTo(sink: BufferedSink) {
          val writer = GrpcMessageSink(
            sink = sink,
            minMessageToCompress = 0,
            messageAdapter = HelloRequest.ADAPTER,
            grpcEncoding = "gzip"
          )
          writer.write(HelloRequest("jesse!"))
        }
      })
      .build()

    val call = client.newCall(request)
    val response = call.execute()
    response.use {
      assertThat(response.code).isEqualTo(200)
      assertThat(response.headers["grpc-status"]).isNull() // Sent in the trailers!
      assertThat(response.headers["grpc-encoding"]).isEqualTo("gzip")
      assertThat(response.body!!.contentType()).isEqualTo("application/grpc".toMediaType())

      val reader = GrpcMessageSource(
        response.body!!.source(), HelloReply.ADAPTER,
        response.header("grpc-encoding")
      )
      assertThat(reader.read()).isEqualTo(HelloReply("howdy, jesse!"))
      assertThat(reader.read()).isNull()
      assertThat(response.trailers()).isEqualTo(headersOf("grpc-status", "0"))
    }
  }

  @Test
  fun serviceThrowsException() {
    helloRpcAction.failNextRequest = true

    val request = Request.Builder()
      .url(jetty.httpsServerUrl!!.resolve("/helloworld.Greeter/SayHello")!!)
      .addHeader("grpc-trace-bin", "")
      .addHeader("grpc-accept-encoding", "gzip")
      .addHeader("grpc-encoding", "gzip")
      .post(object : RequestBody() {
        override fun contentType(): MediaType? {
          return MediaTypes.APPLICATION_GRPC_MEDIA_TYPE
        }

        override fun writeTo(sink: BufferedSink) {
          val writer = GrpcMessageSink(
            sink = sink,
            minMessageToCompress = 0,
            messageAdapter = HelloRequest.ADAPTER,
            grpcEncoding = "gzip"
          )
          writer.write(HelloRequest("jesse!"))
        }
      })
      .build()

    val call = client.newCall(request)
    val response = call.execute()
    response.use {
      assertThat(response.code).isEqualTo(200)
      assertThat(response.headers["grpc-encoding"]).isEqualTo("identity")
      assertThat(response.body!!.contentType()).isEqualTo("application/grpc".toMediaType())
      response.body?.close()
      assertThat(response.trailers()["grpc-status"]).isEqualTo(GrpcStatus.INTERNAL.code.toString())
    }
  }

  @Test
  fun serviceCallTimesOut() {
    helloRpcAction.sleep = Duration.ofMillis(1001)

    val request = Request.Builder()
      .url(jetty.httpsServerUrl!!.resolve("/helloworld.Greeter/SayHello")!!)
      .addHeader("grpc-trace-bin", "")
      .addHeader("grpc-accept-encoding", "gzip")
      .addHeader("grpc-encoding", "gzip")
      .post(object : RequestBody() {
        override fun contentType(): MediaType? {
          return MediaTypes.APPLICATION_GRPC_MEDIA_TYPE
        }

        override fun writeTo(sink: BufferedSink) {
          val writer = GrpcMessageSink(
            sink = sink,
            minMessageToCompress = 0,
            messageAdapter = HelloRequest.ADAPTER,
            grpcEncoding = "gzip"
          )
          writer.write(HelloRequest("jp!"))
        }
      })
      .build()

    assertThrows<InterruptedIOException> { client.newCall(request).execute() }
  }

  @Singleton
  class HelloRpcAction @Inject constructor() : WebAction, GreeterSayHello {
    var failNextRequest = false
    var sleep: Duration = Duration.ofMillis(0)

    override fun sayHello(request: HelloRequest): HelloReply {
      Thread.sleep(sleep.toMillis())
      if (failNextRequest) throw BadRequestException("bad request!")

      return HelloReply.Builder()
        .message("howdy, ${request.name}")
        .build()
    }
  }

  interface GreeterSayHello : Service {
    @WireRpc(
      path = "/helloworld.Greeter/SayHello",
      requestAdapter = "com.squareup.protos.test.grpc.HelloRequest#ADAPTER",
      responseAdapter = "com.squareup.protos.test.grpc.HelloReply#ADAPTER"
    )
    fun sayHello(request: HelloRequest): HelloReply
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(
        WebServerTestingModule(
          webConfig = WebServerTestingModule.TESTING_WEB_CONFIG.copy(grpcGzip = true)
        )
      )
      install(MiskTestingServiceModule())
      install(WebActionModule.create<HelloRpcAction>())
    }
  }

  companion object {
    @JvmStatic
    fun provideMediaTypes() = listOf(MediaTypes.APPLICATION_GRPC_MEDIA_TYPE, MediaTypes.APPLICATION_GRPC_PROTOBUF_MEDIA_TYPE)
  }
}

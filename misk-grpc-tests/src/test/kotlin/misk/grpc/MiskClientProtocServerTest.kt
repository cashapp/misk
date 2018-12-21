package misk.grpc

import com.google.inject.util.Modules
import misk.MiskServiceModule
import misk.grpc.miskclient.MiskGrpcClientModule
import misk.grpc.protocserver.RouteGuideProtocService
import misk.grpc.protocserver.RouteGuideProtocServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okhttp3.internal.http2.Http2
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnJre
import org.junit.jupiter.api.condition.JRE.JAVA_8
import routeguide.Feature
import routeguide.Point
import routeguide.RouteNote
import java.io.IOException
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Provider
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
class MiskClientProtocServerTest {
  @MiskTestModule
  val module = Modules.combine(
      MiskGrpcClientModule(),
      RouteGuideProtocServiceModule(),
      MiskServiceModule())

  @Inject lateinit var grpcClientProvider: Provider<GrpcClient>
  @Inject lateinit var routeGuideProtocService: RouteGuideProtocService

  @BeforeEach
  internal fun setUp() {
    val http2Logger = Logger.getLogger(Http2::class.java.name)
    val consoleHandler = object: Handler() {
      override fun publish(record: LogRecord?) {
//        println(record)
      }

      override fun flush() {
      }

      override fun close() {
      }
    }
    consoleHandler.level = Level.FINE
    http2Logger.addHandler(consoleHandler)
    http2Logger.level = Level.FINE
  }

  @Test
  @DisabledOnJre(JAVA_8) // gRPC needs HTTP/2 which needs ALPN which needs Java 9+.
  fun requestResponse() {
    val grpcMethod = GrpcMethod("/routeguide.RouteGuide/GetFeature",
        routeguide.Point.ADAPTER, routeguide.Feature.ADAPTER)

    val grpcClient = grpcClientProvider.get()
    val features = grpcClient.call(grpcMethod, Point.Builder()
        .latitude(43)
        .longitude(-80)
        .build())
    println(features)
//    assertThat(features).containsExactly(Feature.Builder()
//        .name("pine tree")
//        .location(Point.Builder()
//            .latitude(43)
//            .longitude(-80)
//            .build())
//        .build())
  }

  @Test
  fun bidiStreamingRequest() {
    val grpcMethod = GrpcMethod("/routeguide.RouteGuide/RouteChat",
        routeguide.RouteNote.ADAPTER, routeguide.RouteNote.ADAPTER)

    val grpcClient = grpcClientProvider.get()
    val routeNotes = grpcClient.call(grpcMethod, RouteNote.Builder()
        .message("hello from Benoît")
        .build())
    println(routeNotes)
  }
}

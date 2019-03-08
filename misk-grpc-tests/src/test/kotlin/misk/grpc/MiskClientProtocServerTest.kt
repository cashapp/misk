package misk.grpc

import com.google.inject.util.Modules
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.runBlocking
import misk.MiskServiceModule
import misk.grpc.miskclient.MiskGrpcClientModule
import misk.grpc.protocserver.RouteGuideProtocService
import misk.grpc.protocserver.RouteGuideProtocServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okhttp3.internal.http2.Http2
import org.junit.Rule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnJre
import org.junit.jupiter.api.condition.JRE.JAVA_8
import org.junit.rules.Timeout
import routeguide.Point
import routeguide.RouteNote
import java.util.concurrent.TimeUnit
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Provider

@MiskTest(startService = true)
class MiskClientProtocServerTest {
  @JvmField @Rule val timeout = Timeout(10, TimeUnit.SECONDS)

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
  fun requestResponse(): Unit = runBlocking {

    val grpcMethod = GrpcMethod("/routeguide.RouteGuide/GetFeature",
        routeguide.Point.ADAPTER, routeguide.Feature.ADAPTER, false, false)

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
  fun bidiStreamingRequest(): Unit = runBlocking {

    val grpcMethod = GrpcMethod("/routeguide.RouteGuide/RouteChat",
        routeguide.RouteNote.ADAPTER, routeguide.RouteNote.ADAPTER, true, true)

    val grpcClient = grpcClientProvider.get()
    val routeNotes = grpcClient.call(grpcMethod, RouteNote.Builder()
        .message("hello from Benoît")
        .build())
    println(routeNotes)
  }

  @Test
  fun channels(): Unit = runBlocking {
    println("BEFORE THE STUFF")

    val grpcMethod = GrpcMethod("/routeguide.RouteGuide/RouteChat",
        routeguide.RouteNote.ADAPTER, routeguide.RouteNote.ADAPTER, true, true)

    val responseChannel = Channel<RouteNote>(0)

    val grpcClient = grpcClientProvider.get()
    val requestChannel = grpcClient.call(grpcMethod, responseChannel)

    for (routeNote in responseChannel) {
      requestChannel.send(RouteNote.Builder()
          .message("request " + routeNote.message)
          .build())
      println(routeNote)
    }

    println("AFTER THE LOOP")
    requestChannel.close()
    Unit
  }
}

package misk.grpc

import com.google.inject.util.Modules
import kotlinx.coroutines.runBlocking
import misk.grpc.miskclient.MiskGrpcClientModule
import misk.grpc.miskserver.RouteGuideMiskServiceModule
import misk.logging.LogCollector
import misk.logging.LogCollectorModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.interceptors.RequestLoggingInterceptor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import routeguide.Feature
import routeguide.Point
import routeguide.RouteGuide
import routeguide.RouteNote
import javax.inject.Inject
import javax.inject.Provider

@MiskTest(startService = true)
class MiskClientMiskServerTest {
  @MiskTestModule
  val module = Modules.combine(
      MiskGrpcClientModule(),
      RouteGuideMiskServiceModule(),
      LogCollectorModule())

  @Inject lateinit var routeGuideProvider: Provider<RouteGuide>
  @Inject lateinit var logCollector: LogCollector

  @Test
  fun requestResponse() {
    val point = Point(
        latitude = 43,
        longitude = -80
    )
    val feature = Feature(
        name = "maple tree",
        location = point
    )

    runBlocking {
      val routeGuide = routeGuideProvider.get()

      val returnValue = routeGuide.GetFeature(point)
      assertThat(returnValue).isEqualTo(feature)
    }

    // Confirm interceptors were invoked.
    assertThat(logCollector.takeMessage(RequestLoggingInterceptor::class)).isEqualTo(
        "GetFeatureGrpcAction principal=unknown request=[$point]")
    assertThat(logCollector.takeMessage(RequestLoggingInterceptor::class)).isEqualTo(
        "GetFeatureGrpcAction principal=unknown time=0.000 ns response=$feature")
  }

  @Test
  fun duplexStreaming() {
    runBlocking {
      val routeGuide = routeGuideProvider.get()

      val (sendChannel, receiveChannel) = routeGuide.RouteChat()
      sendChannel.send(RouteNote(message = "a"))
      assertThat(receiveChannel.receive()).isEqualTo(RouteNote(message = "ACK: a"))
      sendChannel.send(RouteNote(message = "b"))
      assertThat(receiveChannel.receive()).isEqualTo(RouteNote(message = "ACK: b"))
      sendChannel.close()
    }

    // Confirm interceptors were invoked.
    assertThat(logCollector.takeMessage(RequestLoggingInterceptor::class)).isEqualTo(
        "RouteChatGrpcAction principal=unknown request=[GrpcMessageSource, GrpcMessageSink]")
    assertThat(logCollector.takeMessage(RequestLoggingInterceptor::class)).isEqualTo(
        "RouteChatGrpcAction principal=unknown time=0.000 ns response=kotlin.Unit")
  }
}

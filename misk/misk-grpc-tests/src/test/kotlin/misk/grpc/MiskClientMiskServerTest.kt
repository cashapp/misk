package misk.grpc

import com.google.inject.util.Modules
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import misk.grpc.miskclient.MiskGrpcClientModule
import misk.grpc.miskserver.RouteChatGrpcAction
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
import routeguide.RouteGuideClient
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

  @Inject lateinit var routeGuideProvider: Provider<RouteGuideClient>
  @Inject lateinit var logCollector: LogCollector
  @Inject lateinit var routeChatGrpcAction: RouteChatGrpcAction

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

      val returnValue = routeGuide.GetFeature().execute(point)
      assertThat(returnValue).isEqualTo(feature)
    }

    // Confirm interceptors were invoked.
    assertThat(logCollector.takeMessages(RequestLoggingInterceptor::class)).containsExactly(
      "GetFeatureGrpcAction principal=unknown time=0.000 ns code=200 request=[Point{latitude=43, longitude=-80}] response=Feature{name=maple tree, location=Point{latitude=43, longitude=-80}}"
    )
  }

  @Test
  fun duplexStreaming() {
    runBlocking {
      val routeGuide = routeGuideProvider.get()

      val (sendChannel, receiveChannel) = routeGuide.RouteChat().execute()
      sendChannel.send(RouteNote(message = "a"))
      assertThat(receiveChannel.receive()).isEqualTo(RouteNote(message = "ACK: a"))
      sendChannel.send(RouteNote(message = "b"))
      assertThat(receiveChannel.receive()).isEqualTo(RouteNote(message = "ACK: b"))
      sendChannel.close()
    }

    // Confirm interceptors were invoked.
    assertThat(logCollector.takeMessages(RequestLoggingInterceptor::class)).containsExactly(
      "RouteChatGrpcAction principal=unknown time=0.000 ns code=200 request=[GrpcMessageSource, GrpcMessageSink] response=kotlin.Unit"
    )
  }

  @Test
  fun duplexStreamingResponseFirst() {
    routeChatGrpcAction.welcomeMessage = "welcome"

    runBlocking {
      val routeGuide = routeGuideProvider.get()

      val (sendChannel, receiveChannel: ReceiveChannel<RouteNote>) =
          routeGuide.RouteChat().execute()
      assertThat(receiveChannel.receive()).isEqualTo(RouteNote(message = "welcome"))
      sendChannel.close()
    }
  }
}

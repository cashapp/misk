package misk.grpc

import com.google.inject.util.Modules
import kotlinx.coroutines.runBlocking
import misk.grpc.miskclient.MiskGrpcClientModule
import misk.grpc.miskserver.RouteGuideMiskServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
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
      RouteGuideMiskServiceModule())

  @Inject lateinit var routeGuideProvider: Provider<RouteGuide>

  @Test
  fun requestResponse() {
    runBlocking {
      val routeGuide = routeGuideProvider.get()

      val feature = routeGuide.GetFeature(Point(
          latitude = 43,
          longitude = -80))
      assertThat(feature).isEqualTo(Feature(
          name = "maple tree",
          location = Point(latitude = 43, longitude = -80)
      ))
    }
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
  }
}

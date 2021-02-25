package misk.grpc

import com.google.inject.util.Modules
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import misk.MiskTestingServiceModule
import misk.grpc.miskclient.MiskGrpcClientModule
import misk.grpc.protocserver.RouteGuideProtocServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import routeguide.Feature
import routeguide.Point
import routeguide.RouteGuideClient
import routeguide.RouteNote
import javax.inject.Inject
import javax.inject.Provider

@MiskTest(startService = true)
class MiskClientProtocServerTest {
  @MiskTestModule
  val module = Modules.combine(
    MiskGrpcClientModule(),
    RouteGuideProtocServiceModule(),
    MiskTestingServiceModule()
  )

  @Inject lateinit var routeGuideProvider: Provider<RouteGuideClient>

  @Test
  fun requestResponse() {
    runBlocking {
      val routeGuide = routeGuideProvider.get()

      val feature = routeGuide.GetFeature().execute(Point(latitude = 43, longitude = -80))
      assertThat(feature).isEqualTo(
        Feature(
          name = "pine tree",
          location = Point(latitude = 43, longitude = -80)
        )
      )
    }
  }

  @Test
  fun streamingResponse() {
    runBlocking {
      val routeGuide = routeGuideProvider.get()

      val (sendChannel, receiveChannel) = routeGuide.RouteChat().executeIn(GlobalScope)
      sendChannel.send(RouteNote(message = "Taco cat"))
      assertThat(receiveChannel.receive().message).isEqualTo("tac ocaT")
      sendChannel.send(RouteNote(message = "A nut for a jar of tuna"))
      assertThat(receiveChannel.receive().message).isEqualTo("anut fo raj a rof tun A")
      sendChannel.close()
    }
  }
}

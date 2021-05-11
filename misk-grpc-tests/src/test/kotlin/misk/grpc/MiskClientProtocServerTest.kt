package misk.grpc

import com.google.inject.Guice
import com.google.inject.util.Modules
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import misk.MiskTestingServiceModule
import misk.grpc.miskclient.MiskGrpcClientModule
import misk.grpc.protocserver.RouteGuideProtocServiceModule
import misk.inject.getInstance
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okhttp3.HttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import routeguide.Feature
import routeguide.Point
import routeguide.RouteGuideClient
import routeguide.RouteNote

@MiskTest(startService = true)
class MiskClientProtocServerTest {
  @MiskTestModule
  val module = Modules.combine(
    RouteGuideProtocServiceModule(),
    MiskTestingServiceModule()
  )

  @Inject @field:Named("grpc server") lateinit var serverUrl: HttpUrl

  private lateinit var routeGuide: RouteGuideClient

  @BeforeEach
  fun createClient() {
    val clientInjector = Guice.createInjector(MiskGrpcClientModule(serverUrl))
    routeGuide = clientInjector.getInstance()
  }

  @Test
  fun requestResponse() {
    runBlocking {
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
      val (sendChannel, receiveChannel) = routeGuide.RouteChat().executeIn(GlobalScope)
      sendChannel.send(RouteNote(message = "Taco cat"))
      assertThat(receiveChannel.receive().message).isEqualTo("tac ocaT")
      sendChannel.send(RouteNote(message = "A nut for a jar of tuna"))
      assertThat(receiveChannel.receive().message).isEqualTo("anut fo raj a rof tun A")
      sendChannel.close()
    }
  }
}

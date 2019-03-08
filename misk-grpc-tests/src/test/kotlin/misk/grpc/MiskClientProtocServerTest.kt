package misk.grpc

import com.google.inject.util.Modules
import com.squareup.wire.GrpcClient
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
import routeguide.RouteGuide
import javax.inject.Inject
import javax.inject.Provider

@MiskTest(startService = true)
class MiskClientProtocServerTest {
  @MiskTestModule
  val module = Modules.combine(
      MiskGrpcClientModule(),
      RouteGuideProtocServiceModule(),
      MiskTestingServiceModule())

  @Inject lateinit var routeGuide: RouteGuide

  @Test
  fun requestResponse() {
    runBlocking {
      val feature = routeGuide.GetFeature(Point(latitude = 43, longitude = -80))
      assertThat(feature).isEqualTo(
          Feature(name = "pine tree", location = Point(latitude = 43, longitude = -80)))
    }
  }
}

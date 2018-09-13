package misk.grpc

import com.google.inject.util.Modules
import misk.MiskServiceModule
import misk.grpc.miskclient.MiskGrpcClientModule
import misk.grpc.protocserver.RouteGuideProtocServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnJre
import org.junit.jupiter.api.condition.JRE.JAVA_8
import routeguide.Feature
import routeguide.Point
import javax.inject.Inject
import javax.inject.Provider

@MiskTest(startService = true)
class MiskClientProtocServerTest {
  @MiskTestModule
  val module = Modules.combine(
      MiskGrpcClientModule(),
      RouteGuideProtocServiceModule(),
      MiskServiceModule())

  @Inject lateinit var grpcClientProvider: Provider<GrpcClient>

  @Test
  @DisabledOnJre(JAVA_8) // gRPC needs HTTP/2 which needs ALPN which needs Java 9+.
  fun requestResponse() {
    val grpcMethod = GrpcMethod("/routeguide.RouteGuide/GetFeature",
        routeguide.Point.ADAPTER, routeguide.Feature.ADAPTER)

    val grpcClient = grpcClientProvider.get()
    val feature = grpcClient.call(grpcMethod, Point.Builder()
        .latitude(43)
        .longitude(-80)
        .build())
    assertThat(feature).isEqualTo(Feature.Builder()
        .name("pine tree")
        .location(Point.Builder()
            .latitude(43)
            .longitude(-80)
            .build())
        .build())
  }
}

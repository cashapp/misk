package misk.grpc

import com.google.inject.util.Modules
import misk.grpc.miskclient.MiskGrpcClientModule
import misk.grpc.miskserver.RouteGuideMiskServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import routeguide.Feature
import routeguide.Point
import javax.inject.Inject
import javax.inject.Provider

@MiskTest(startService = true)
class MiskClientMiskServerTest {
  @MiskTestModule
  val module = Modules.combine(
      MiskGrpcClientModule(),
      RouteGuideMiskServiceModule())

  @Inject lateinit var grpcClientProvider: Provider<GrpcClient>

  @Test
  fun requestResponse() {
    val grpcMethod = GrpcMethod("/routeguide.RouteGuide/GetFeature",
        routeguide.Point.ADAPTER, routeguide.Feature.ADAPTER)

    val grpcClient = grpcClientProvider.get()
    val feature = grpcClient.call(grpcMethod, Point.Builder()
        .latitude(43)
        .longitude(-80)
        .build())
    assertThat(feature).isEqualTo(Feature.Builder()
        .name("maple tree")
        .location(Point.Builder()
            .latitude(43)
            .longitude(-80)
            .build())
        .build())
  }
}

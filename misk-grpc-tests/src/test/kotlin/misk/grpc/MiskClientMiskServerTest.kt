package misk.grpc

import com.google.inject.util.Modules
import kotlinx.coroutines.experimental.runBlocking
import misk.grpc.miskclient.MiskGrpcClientModule
import misk.grpc.miskserver.RouteGuideMiskServiceModule
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
class MiskClientMiskServerTest {
  @MiskTestModule
  val module = Modules.combine(
      MiskGrpcClientModule(),
      RouteGuideMiskServiceModule())

  @Inject lateinit var grpcClientProvider: Provider<GrpcClient>

  @Test
  @DisabledOnJre(JAVA_8) // gRPC needs HTTP/2 which needs ALPN which needs Java 9+.
  fun requestResponse(): Unit = runBlocking {
    val grpcMethod = GrpcMethod("/routeguide.RouteGuide/GetFeature",
        routeguide.Point.ADAPTER, routeguide.Feature.ADAPTER)

    val grpcClient = grpcClientProvider.get()
    val features = grpcClient.call(grpcMethod, Point.Builder()
        .latitude(43)
        .longitude(-80)
        .build())
    assertThat(features).containsExactly(Feature.Builder()
        .name("maple tree")
        .location(Point.Builder()
            .latitude(43)
            .longitude(-80)
            .build())
        .build())
    Unit
  }
}

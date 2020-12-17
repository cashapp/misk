package misk.grpc.vanilla

import com.google.inject.util.Modules
import io.grpc.ManagedChannel
import misk.MiskTestingServiceModule
import misk.grpc.vanilla.protocclient.ProtocGrpcClientModule
import misk.grpc.vanilla.wireserver.RouteGuideWireGrpc
import misk.grpc.vanilla.wireserver.RouteGuideWireServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import routeguide.Feature
import routeguide.Point
import javax.inject.Inject
import javax.inject.Provider

@MiskTest(startService = true)
class WireClientWireServerTest {
  @MiskTestModule
  val module = Modules.combine(
    ProtocGrpcClientModule(),
    RouteGuideWireServiceModule(),
    MiskTestingServiceModule()
  )

  @Inject lateinit var channelProvider: Provider<ManagedChannel>

  @Test
  fun requestResponse() {
    val channel = channelProvider.get()
    val stub = RouteGuideWireGrpc.newBlockingStub(channel)

    val feature = stub.getFeature(Point(43, -80))
    assertThat(feature).isEqualTo(Feature("pine tree", Point(43, -80)))
  }
}

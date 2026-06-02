package misk.grpc

import com.google.inject.Provider
import com.google.inject.util.Modules
import io.grpc.ManagedChannel
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.grpc.protocclient.ProtocGrpcClientModule
import misk.grpc.protocserver.RouteGuideProtocServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import routeguide.RouteGuideGrpc
import routeguide.RouteGuideProto.Feature
import routeguide.RouteGuideProto.Point

@MiskTest(startService = true)
class ProtocClientProtocServerTest {
  @MiskTestModule
  val module = Modules.combine(ProtocGrpcClientModule(), RouteGuideProtocServiceModule(), MiskTestingServiceModule())

  @Inject lateinit var channelProvider: Provider<ManagedChannel>

  @Test
  fun requestResponse() {
    val channel = channelProvider.get()
    val stub = RouteGuideGrpc.newBlockingStub(channel)

    val feature = stub.getFeature(Point.newBuilder().setLatitude(43).setLongitude(-80).build())
    assertThat(feature)
      .isEqualTo(
        Feature.newBuilder()
          .setName("pine tree")
          .setLocation(Point.newBuilder().setLatitude(43).setLongitude(-80).build())
          .build()
      )
  }
}

package misk.grpc

import com.google.inject.util.Modules
import io.grpc.ManagedChannelBuilder
import misk.MiskServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import routeguide.RouteGuideGrpc
import routeguide.RouteGuideProto.Feature
import routeguide.RouteGuideProto.Point
import javax.inject.Inject

@MiskTest(startService = true)
class ProtocClientProtocServerTest {
  @MiskTestModule
  val module = Modules.combine(MiskServiceModule(), RouteGuideProtocServiceModule())

  @Inject lateinit var protocGrpcService: ProtocGrpcService

  @Test
  fun test() {
    val channel = ManagedChannelBuilder.forAddress("localhost", protocGrpcService.port)
        .usePlaintext()
        .build()
    val stub = RouteGuideGrpc.newBlockingStub(channel)
    val feature = stub.getFeature(Point.newBuilder()
        .setLatitude(43)
        .setLongitude(-80)
        .build())
    assertThat(feature).isEqualTo(Feature.newBuilder()
        .setName("maple tree")
        .setLocation(Point.newBuilder()
            .setLatitude(43)
            .setLongitude(-80)
            .build())
        .build())
  }
}

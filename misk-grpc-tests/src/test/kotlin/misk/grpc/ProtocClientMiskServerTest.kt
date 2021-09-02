package misk.grpc

import com.google.inject.util.Modules
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.protobuf.StatusProto
import kotlin.test.assertFailsWith
import misk.grpc.miskserver.RouteGuideMiskServiceModule
import misk.grpc.protocclient.ProtocGrpcClientModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import routeguide.RouteGuideGrpc
import routeguide.RouteGuideProto.Feature
import routeguide.RouteGuideProto.Point
import routeguide.RouteGuideProto.Rectangle
import javax.inject.Inject
import javax.inject.Provider

@MiskTest(startService = true)
class ProtocClientMiskServerTest {
  @MiskTestModule
  val module = Modules.combine(
    RouteGuideMiskServiceModule(),
    ProtocGrpcClientModule()
  )

  @Inject lateinit var channelProvider: Provider<ManagedChannel>

  @Test
  fun requestResponse() {
    val channel = channelProvider.get()
    val stub = RouteGuideGrpc.newBlockingStub(channel)

    val feature = stub.getFeature(
      Point.newBuilder()
        .setLatitude(43)
        .setLongitude(-80)
        .build()
    )
    assertThat(feature).isEqualTo(
      Feature.newBuilder()
        .setName("maple tree")
        .setLocation(
          Point.newBuilder()
            .setLatitude(43)
            .setLongitude(-80)
            .build()
        )
        .build()
    )
  }

  @Test
  fun crossStatusError() {
    val channel = channelProvider.get()
    val stub = RouteGuideGrpc.newBlockingStub(channel)

    val e = assertFailsWith<StatusRuntimeException> {
      val feature = stub.getFeature(
        Point.newBuilder().setLatitude(-200).build()
      )
    }

    val s = StatusProto.fromThrowable(e)
    assertThat(s).isNotNull
    requireNotNull(s)
    assertThat(s.code).isEqualTo(Status.INVALID_ARGUMENT.code.value())
    assertThat(s.message).isEqualTo("invalid coordinates")
    assertThat(s.detailsCount).isEqualTo(2)
    assertThat(s.getDetails(0).unpack(Rectangle::class.java)).isEqualTo(
      Rectangle.newBuilder()
        .setLo(Point.newBuilder().setLatitude(-90).setLongitude(-180))
        .setHi(Point.newBuilder().setLatitude(90).setLongitude(180))
        .build()
    )
    assertThat(s.getDetails(1).unpack(Point::class.java)).isEqualTo(
      Point.newBuilder().setLatitude(-200).setLongitude(0).build()
    )
  }
}

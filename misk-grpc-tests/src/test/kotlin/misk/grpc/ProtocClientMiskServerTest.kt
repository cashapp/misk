package misk.grpc

import com.google.inject.util.Modules
import misk.grpc.miskserver.RouteGuideMiskServiceModule
import misk.grpc.protocclient.GrpcChannelFactory
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Http2Testing
import misk.web.jetty.JettyService
import okhttp3.HttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import routeguide.RouteGuideGrpc
import routeguide.RouteGuideProto.Feature
import routeguide.RouteGuideProto.Point
import java.net.InetSocketAddress
import javax.inject.Inject

@MiskTest(startService = true)
class ProtocClientMiskServerTest {
  @MiskTestModule
  val module = Modules.combine(RouteGuideMiskServiceModule())

  @Inject lateinit var jetty: JettyService
  @Inject lateinit var grpcChannelFactory: GrpcChannelFactory

  @Test
  fun requestResponse() {
    assumeTrue(Http2Testing.isJava9OrNewer())

    val channel = grpcChannelFactory.createClientChannel(jetty.httpsServerUrl!!.toSocketAddress())
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

  private fun HttpUrl.toSocketAddress() = InetSocketAddress(host(), port())
}

package misk.grpc.vanilla.protocclient

import com.google.inject.Provides
import misk.inject.KAbstractModule
import okhttp3.HttpUrl
import java.net.InetSocketAddress
import javax.inject.Named
import javax.inject.Singleton

/* A module that creates a Google gRPC connection that can then be used to construct clients */
class ProtocGrpcClientModule : KAbstractModule() {
  override fun configure() {}

  @Provides
  @Singleton
  fun provideChannel(
    @Named("grpc server") url: HttpUrl,
    grpcChannelFactory: GrpcChannelFactory
  ) = grpcChannelFactory.createClientChannel(InetSocketAddress(url.host, url.port))
}

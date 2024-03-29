package misk.grpc.protocclient

import com.google.inject.Provides
import misk.inject.KAbstractModule
import okhttp3.HttpUrl
import java.net.InetSocketAddress
import jakarta.inject.Named
import jakarta.inject.Singleton

class ProtocGrpcClientModule : KAbstractModule() {
  override fun configure() {}

  @Provides
  @Singleton
  fun provideChannel(
    @Named("grpc server") url: HttpUrl,
    grpcChannelFactory: GrpcChannelFactory
  ) = grpcChannelFactory.createClientChannel(InetSocketAddress(url.host, url.port))
}

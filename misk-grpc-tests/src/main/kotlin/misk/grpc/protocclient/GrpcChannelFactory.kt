package misk.grpc.protocclient

import io.grpc.ManagedChannel
import io.grpc.netty.GrpcSslContexts
import io.grpc.netty.NettyChannelBuilder
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.SocketAddress
import misk.resources.ResourceLoader

@Singleton
class GrpcChannelFactory @Inject constructor(val resourceLoader: ResourceLoader) {
  @JvmOverloads
  fun createClientChannel(
    serverAddress: SocketAddress,
    serverCertResource: String = "classpath:/ssl/server_cert.pem",
  ): ManagedChannel {
    return NettyChannelBuilder.forAddress(serverAddress)
      .sslContext(createClientSslContext(serverCertResource))
      .useTransportSecurity()
      .build()
  }

  private fun createClientSslContext(serverCertResource: String): SslContext? {
    val builder = SslContextBuilder.forClient()

    resourceLoader.open(serverCertResource)!!.use { builder.trustManager(it.inputStream()) }

    GrpcSslContexts.configure(builder)

    return builder.build()
  }
}

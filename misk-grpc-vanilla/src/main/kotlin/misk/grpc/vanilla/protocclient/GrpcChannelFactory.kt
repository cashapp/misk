package misk.grpc.vanilla.protocclient

import io.grpc.ManagedChannel
import io.grpc.netty.GrpcSslContexts
import io.grpc.netty.NettyChannelBuilder
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import misk.resources.ResourceLoader
import java.net.SocketAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrpcChannelFactory @Inject constructor(val resourceLoader: ResourceLoader) {
  fun createClientChannel(
    serverAddress: SocketAddress,
    serverCertResource: String = "classpath:/ssl/server_cert.pem"
  ): ManagedChannel {
    return NettyChannelBuilder.forAddress(serverAddress)
        .sslContext(createClientSslContext(serverCertResource))
        .useTransportSecurity()
        .build()
  }

  private fun createClientSslContext(serverCertResource: String): SslContext? {
    val builder = SslContextBuilder.forClient()

    resourceLoader.open(serverCertResource)!!.use {
      builder.trustManager(it.inputStream())
    }

    GrpcSslContexts.configure(builder)

    return builder.build()
  }
}

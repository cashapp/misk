package misk.grpc.protocserver

import com.google.common.util.concurrent.AbstractIdleService
import io.grpc.BindableService
import io.grpc.Server
import io.grpc.ServerBuilder
import misk.resources.ResourceLoader
import java.net.InetSocketAddress
import java.net.SocketAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a standard gRPC server: generated protoc protos and a Netty backend. This isn't how Misk
 * does gRPC, but it should be useful to confirm interoperability.
 */
@Singleton
class ProtocGrpcService : AbstractIdleService() {
  @Inject lateinit var services: List<BindableService>
  @Inject lateinit var resourceLoader: ResourceLoader

  lateinit var server: Server

  val socketAddress: SocketAddress
    get() = InetSocketAddress("127.0.0.1", server.port)

  override fun startUp() {
    val serverBuilder = ServerBuilder.forPort(0)

    resourceLoader.open("classpath:/ssl/server_cert.pem")!!.use { certificate ->
      resourceLoader.open("classpath:/ssl/server_key.pem")!!.use { privateKey ->
        serverBuilder.useTransportSecurity(certificate.inputStream(), privateKey.inputStream())
      }
    }

    for (service in services) {
      serverBuilder.addService(service)
    }
    server = serverBuilder.build()
    server.start()
  }

  override fun shutDown() {
    server.shutdown()
    server.awaitTermination()
  }
}
package misk.grpc

import com.google.common.util.concurrent.AbstractIdleService
import io.grpc.BindableService
import io.grpc.Server
import io.grpc.ServerBuilder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a standard gRPC server: generated protoc protos and a Netty backend. This isn't how Misk
 * does gRPC, but it should be useful to confirm interoperability.
 */
@Singleton
class ProtocGrpcService : AbstractIdleService() {
  @Inject lateinit var services: List<BindableService>
  lateinit var server: Server

  val port: Int
    get() = server.port

  override fun startUp() {
    val serverBuilder = ServerBuilder.forPort(0)
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
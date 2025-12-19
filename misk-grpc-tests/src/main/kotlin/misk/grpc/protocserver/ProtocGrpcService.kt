package misk.grpc.protocserver

import com.google.common.util.concurrent.AbstractIdleService
import io.grpc.BindableService
import io.grpc.Server
import io.grpc.ServerBuilder
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.resources.ResourceLoader
import okhttp3.HttpUrl

/**
 * Runs a standard gRPC server: generated protoc protos and a Netty backend. This isn't how Misk does gRPC, but it
 * should be useful to confirm interoperability.
 */
@Singleton
class ProtocGrpcService
@Inject
constructor(private val services: List<BindableService>, private val resourceLoader: ResourceLoader) :
  AbstractIdleService() {

  lateinit var server: Server

  val url: HttpUrl
    get() = HttpUrl.Builder().scheme("https").host("127.0.0.1").port(server.port).build()

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

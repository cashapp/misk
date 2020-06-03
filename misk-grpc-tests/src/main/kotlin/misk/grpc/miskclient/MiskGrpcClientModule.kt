package misk.grpc.miskclient

import com.google.inject.Provides
import misk.client.GrpcClientModule
import misk.client.HttpClientsConfig
import misk.endpoints.HttpClientConfig
import misk.endpoints.HttpClientSSLConfig
import misk.endpoints.buildClientEndpointConfig
import misk.inject.KAbstractModule
import misk.security.ssl.SslLoader
import misk.security.ssl.TrustStoreConfig
import okhttp3.HttpUrl
import routeguide.GrpcRouteGuideClient
import routeguide.RouteGuideClient
import javax.inject.Named
import javax.inject.Singleton

class MiskGrpcClientModule : KAbstractModule() {
  override fun configure() {
    install(GrpcClientModule.create<RouteGuideClient, GrpcRouteGuideClient>("default"))
  }

  @Provides
  @Singleton
  fun provideHttpClientsConfig(@Named("grpc server") url: HttpUrl) = HttpClientsConfig(
      "default" to url.buildClientEndpointConfig(defaultHttpClientConfig)
  )

  private val defaultHttpClientConfig = HttpClientConfig(
      ssl = HttpClientSSLConfig(
          cert_store = null,
          trust_store = TrustStoreConfig(
              resource = "classpath:/ssl/server_cert.pem",
              format = SslLoader.FORMAT_PEM
          )
      )
  )
}

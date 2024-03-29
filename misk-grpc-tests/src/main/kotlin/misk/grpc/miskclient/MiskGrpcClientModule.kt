package misk.grpc.miskclient

import com.google.inject.Provides
import jakarta.inject.Singleton
import misk.MiskTestingServiceModule
import misk.client.ClientNetworkInterceptor
import misk.client.GrpcClientModule
import misk.client.HttpClientConfig
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientSSLConfig
import misk.client.HttpClientsConfig
import misk.inject.KAbstractModule
import misk.security.ssl.SslLoader
import misk.security.ssl.TrustStoreConfig
import okhttp3.HttpUrl
import routeguide.GrpcRouteGuideClient
import routeguide.RouteGuideClient

class MiskGrpcClientModule(val url: HttpUrl) : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(GrpcClientModule.create<RouteGuideClient, GrpcRouteGuideClient>("default"))
    multibind<ClientNetworkInterceptor.Factory>().to<RouteGuideCallCounter>()
  }

  @Provides
  @Singleton
  fun provideHttpClientsConfig(): HttpClientsConfig {
    return HttpClientsConfig(
      endpoints = mapOf(
        "default" to HttpClientEndpointConfig(
          url = url.toString(),
          clientConfig = HttpClientConfig(
            ssl = HttpClientSSLConfig(
              cert_store = null,
              trust_store = TrustStoreConfig(
                resource = "classpath:/ssl/server_cert.pem",
                format = SslLoader.FORMAT_PEM
              )
            )
          )
        )
      )
    )
  }
}

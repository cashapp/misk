package misk.grpc.miskclient

import com.google.inject.Provides
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientModule
import misk.client.HttpClientSSLConfig
import misk.client.HttpClientsConfig
import misk.grpc.GrpcClient
import misk.inject.KAbstractModule
import misk.security.ssl.SslLoader
import misk.security.ssl.TrustStoreConfig
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

class MiskGrpcClientModule : KAbstractModule() {
  override fun configure() {
    install(HttpClientModule("default"))
  }

  @Provides
  @Singleton
  fun provideHttpClientsConfig(): HttpClientsConfig {
    return HttpClientsConfig(
        endpoints = mapOf(
            "default" to HttpClientEndpointConfig(
                "http://example.com/",
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
  }

  @Provides
  @Singleton
  fun provideGrpcClient(
    client: OkHttpClient,
    @Named("grpc server") url: HttpUrl
  ) = GrpcClient(client, url)
}
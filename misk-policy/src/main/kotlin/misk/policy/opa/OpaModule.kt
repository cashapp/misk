package misk.policy.opa

import com.google.inject.Provides
import com.squareup.moshi.Moshi
import misk.client.HttpClientConfig
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientFactory
import misk.inject.KAbstractModule
import retrofit2.converter.scalars.ScalarsConverterFactory
import wisp.moshi.buildMoshi
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

class OpaModule @Inject constructor(
  private val config: OpaConfig
) : KAbstractModule() {
  override fun configure() {
    require(config.baseUrl.isNotBlank())
    bind<OpaConfig>().toInstance(config)
    bind<OpaPolicyEngine>().to<RealOpaPolicyEngine>()
  }

  @Provides
  internal fun opaConfig(
    config: OpaConfig
  ): Boolean {
    return config.provenance
  }

  @Provides
  internal fun opaApi(
    config: OpaConfig,
    httpClientFactory: HttpClientFactory,
    ): OpaApi {

    val builder = retrofit2.Retrofit.Builder()
      .addConverterFactory(ScalarsConverterFactory.create())

    if (!config.unixSocket.isNullOrEmpty()) {
      val httpClientConfig = HttpClientConfig(unixSocketFile = config.unixSocket)
      val okHttpClient = httpClientFactory.create(
        HttpClientEndpointConfig(url = config.unixSocket, clientConfig = httpClientConfig)
      )
      builder.client(okHttpClient)
    }
    builder.baseUrl(config.baseUrl)
    return builder.build().create(OpaApi::class.java)
  }

  @Provides @Singleton @Named("opa-moshi")
  fun provideMoshi(): Moshi {
    return buildMoshi(emptyList())
  }
}

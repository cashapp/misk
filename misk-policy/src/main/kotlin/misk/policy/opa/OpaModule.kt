package misk.policy.opa

import com.google.inject.Provides
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import misk.client.HttpClientFactory
import misk.inject.KAbstractModule
import retrofit2.converter.scalars.ScalarsConverterFactory
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

class OpaModule @Inject constructor(
  private val config: OpaConfig
) : KAbstractModule() {
  override fun configure() {
    require(config.baseUrl.isNotBlank())
    bind<OpaConfig>().toInstance(config)
  }

  @Provides
  internal fun opaApi(
    config: OpaConfig,
  ): OpaApi {
    val retrofit = retrofit2.Retrofit.Builder()
      .addConverterFactory(ScalarsConverterFactory.create())
      .baseUrl(config.baseUrl)
      .build()
    return retrofit.create(OpaApi::class.java)
  }

  @Provides @Singleton @Named("opa-moshi")
  fun provideMoshi(): Moshi {
    return Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
  }
}

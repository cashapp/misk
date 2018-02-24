package misk.client

import com.google.inject.Key
import com.google.inject.Provider
import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Inject
import kotlin.reflect.KClass

/** Creates a retrofit-backed typed client given an API interface and an HTTP configuration */
class TypedHttpClientModule<T : Any>(
  private val kclass: KClass<T>,
  private val name: String,
  private val annotation: Annotation? = null
) : KAbstractModule() {
  override fun configure() {
    install(HttpClientModule(name, annotation))

    val key = if (annotation != null) Key.get(kclass.java, annotation) else Key.get(kclass.java)
    bind(key).toProvider(TypedClientProvider(kclass, name))
  }

  companion object {
    inline fun <reified T : Any> create(
      name: String,
      annotation: Annotation? = null
    ): TypedHttpClientModule<T> {
      return TypedHttpClientModule(T::class, name, annotation)
    }
  }

  private class TypedClientProvider<T : Any>(
    val kclass: KClass<T>,
    val name: String
  ) : Provider<T> {
    @Inject
    private lateinit var httpClientsConfig: HttpClientsConfig

    @Inject
    private lateinit var moshi: Moshi

    override fun get(): T {
      val okhttp = httpClientsConfig.newHttpClient(name)
      val retrofit = Retrofit.Builder()
          .baseUrl(httpClientsConfig.endpoints[name]?.url!!)
          .client(okhttp)
          .addConverterFactory(MoshiConverterFactory.create(moshi))
          .build()
      return retrofit.create(kclass.java)
    }
  }
}

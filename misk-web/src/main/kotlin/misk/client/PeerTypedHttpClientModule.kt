package misk.client

import com.google.inject.Inject
import com.google.inject.Key
import com.google.inject.Provider
import com.google.inject.util.Types
import misk.clustering.Cluster
import misk.inject.KAbstractModule
import retrofit2.Retrofit
import kotlin.reflect.KClass

/**
 * Creates a retrofit-backed typed client factory given an API interface and an HTTP configuration.
 *
 * The factory returned typed clients that can be used to call other members of the cluster.
 */
class TypedPeerHttpClientModule<T : Any>(
  private val kclass: KClass<T>,
  private val name: String,
  private val retrofitBuilderProvider: Provider<Retrofit.Builder>? = null
) : KAbstractModule() {

    override fun configure() {
        requireBinding(PeerClientFactory::class.java)

        // Initialize empty sets for our multibindings.
        newMultibinder<ClientNetworkInterceptor.Factory>()
        newMultibinder<ClientApplicationInterceptor.Factory>()

        @Suppress("UNCHECKED_CAST")
        val key = Key.get(
                Types.newParameterizedType(TypedPeerClientFactory::class.java,
                        kclass.java)) as Key<TypedPeerClientFactory<T>>

        bind(key).toProvider(PeerTypedClientProvider(kclass, name, retrofitBuilderProvider))
    }

    companion object {
        inline fun <reified T : Any> create(name: String): TypedPeerHttpClientModule<T> {
            return TypedPeerHttpClientModule(T::class, name)
        }
    }

    private class PeerTypedClientProvider<T : Any>(
      kclass: KClass<T>,
      name: String,
      retrofitBuilderProvider: Provider<Retrofit.Builder>?
    ) : TypedClientFactory<T>(kclass, name, retrofitBuilderProvider),
            Provider<TypedPeerClientFactory<T>> {

        @Inject
        private lateinit var peerClientFactory: PeerClientFactory

        override fun get(): TypedPeerClientFactory<T> {
            return object : TypedPeerClientFactory<T> {
                override fun client(peer: Cluster.Member): T {
                    return typedClient(peerClientFactory.client(peer), peerClientFactory.baseUrl(peer))
                }
            }
        }
    }
}
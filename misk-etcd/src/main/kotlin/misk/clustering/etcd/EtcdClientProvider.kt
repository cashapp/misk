package misk.clustering.etcd

import com.coreos.jetcd.Client
import javax.inject.Inject
import javax.inject.Provider

internal class EtcdClientProvider @Inject internal constructor(
  private val config: EtcdConfig
) : Provider<Client> {
  override fun get(): Client {
    // TODO(mmihic): SSL and auth stuff
    return Client.builder().
        endpoints(*config.endpoints.toTypedArray()).
        lazyInitialization(true).
        build()
  }
}
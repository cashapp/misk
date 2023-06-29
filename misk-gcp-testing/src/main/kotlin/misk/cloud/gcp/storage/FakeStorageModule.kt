package misk.cloud.gcp.storage

import com.google.cloud.NoCredentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.inject.KAbstractModule

/** Installs an embeddable version of [Storage] that works in-memory */
@Deprecated("Replace the dependency on misk-gcp-testing with testFixtures(misk-gcp)")
class FakeStorageModule : KAbstractModule() {
  override fun configure() {}

  @Provides
  @Singleton
  fun provideStorage(): Storage = StorageOptions.newBuilder()
    .setCredentials(NoCredentials.getInstance())
    .setServiceRpcFactory({ _ -> InMemoryStorageRpc() })
    .build()
    .service
}

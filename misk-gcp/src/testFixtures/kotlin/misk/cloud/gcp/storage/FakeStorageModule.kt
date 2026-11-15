package misk.cloud.gcp.storage

import com.google.cloud.NoCredentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import com.google.inject.Provides
import jakarta.inject.Singleton
import misk.inject.KAbstractModule
import misk.testing.TestFixture

/** Installs an embeddable version of [Storage] that works in-memory */
class FakeStorageModule : KAbstractModule() {
  override fun configure() {
    multibind<TestFixture>().to<InMemoryStorageRpc>()
  }

  @Provides
  @Singleton
  fun provideStorage(): Storage =
    StorageOptions.newBuilder()
      .setCredentials(NoCredentials.getInstance())
      .setServiceRpcFactory({ _ -> InMemoryStorageRpc() })
      .build()
      .service
}

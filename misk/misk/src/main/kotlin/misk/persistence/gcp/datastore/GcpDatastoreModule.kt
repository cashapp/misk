package misk.persistence.gcp.datastore

import com.google.cloud.datastore.Datastore
import com.google.cloud.datastore.DatastoreOptions
import com.google.cloud.datastore.DatastoreReader
import com.google.inject.AbstractModule
import com.google.inject.Provides
import javax.inject.Singleton

class GcpDatastoreModule : AbstractModule() {
  override fun configure() {
  }

  @Singleton
  @Provides
  fun provideDatstore(config: GcpDatastoreConfig): Datastore {
    return DatastoreOptions.newBuilder()
        .setNamespace(config.namespace)
        .build().service
  }

  @Singleton
  @Provides
  fun provideDatastoreReader(
    datastore: Datastore
  ): DatastoreReader {
    return datastore
  }
}


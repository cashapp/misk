package misk.cloud.gcp

import misk.cloud.gcp.datastore.FakeDatastoreModule
import misk.cloud.gcp.storage.FakeStorageModule
import misk.inject.KAbstractModule

/** Installs testing support for running against google cloud emulators */
internal class FakeGoogleCloudModule : KAbstractModule() {
  override fun configure() {
    install(FakeDatastoreModule())
    install(FakeStorageModule())
  }
}

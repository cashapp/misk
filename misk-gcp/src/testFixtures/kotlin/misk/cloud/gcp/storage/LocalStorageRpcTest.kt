package misk.cloud.gcp.storage

import jakarta.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TemporaryFolder
import misk.testing.TemporaryFolderModule
import wisp.moshi.defaultKotlinMoshi

@MiskTest(startService = false)
internal class LocalStorageRpcTest : CustomStorageRpcTestCases<LocalStorageRpc>() {
  @MiskTestModule val module = TemporaryFolderModule()

  @Inject private lateinit var tempFolder: TemporaryFolder

  override fun newStorageRpc() = LocalStorageRpc(tempFolder.newFolder(), defaultKotlinMoshi)
}

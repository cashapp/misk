package misk.cloud.gcp.storage

import com.squareup.moshi.Moshi
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TemporaryFolder
import misk.testing.TemporaryFolderModule
import javax.inject.Inject
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@MiskTest(startService = false)
internal class LocalStorageRpcTest : CustomStorageRpcTestCases<LocalStorageRpc>() {
  @MiskTestModule
  val module = TemporaryFolderModule()

  @Inject
  private lateinit var tempFolder: TemporaryFolder

  override fun newStorageRpc() = LocalStorageRpc(tempFolder.newFolder(), Moshi.Builder()
      .add(KotlinJsonAdapterFactory()) // Added last for lowest precedence.
      .build())
}

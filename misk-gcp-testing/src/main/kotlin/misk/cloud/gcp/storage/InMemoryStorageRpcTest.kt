package misk.cloud.gcp.storage

@Deprecated("Replace the dependency on misk-gcp-testing with testFixtures(misk-gcp)")
internal class InMemoryStorageRpcTest : CustomStorageRpcTestCases<InMemoryStorageRpc>() {
  override fun newStorageRpc() = InMemoryStorageRpc()
}

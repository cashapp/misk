package misk.cloud.gcp.storage

internal class InMemoryStorageRpcTest : CustomStorageRpcTestCases<InMemoryStorageRpc>() {
  override fun newStorageRpc() = InMemoryStorageRpc()
}

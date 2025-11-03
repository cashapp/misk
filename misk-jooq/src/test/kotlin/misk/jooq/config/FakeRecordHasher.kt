package misk.jooq.config

import misk.jooq.listeners.RecordHasher

// Fake implementation for testing
class FakeRecordHasher : RecordHasher {

  override fun computeMac(keyName: String, data: ByteArray): ByteArray {
    // Simple hash for testing - not secure!
    val fakeMac = "$keyName:${data.contentHashCode()}".toByteArray()
    return fakeMac
  }

  override fun verifyMac(keyName: String, providedMac: ByteArray, data: ByteArray) {
    val expectedFakeMac = "$keyName:${data.contentHashCode()}".toByteArray()
    if (!providedMac.contentEquals(expectedFakeMac)) {
      throw SecurityException("MAC verification failed")
    }
  }
}

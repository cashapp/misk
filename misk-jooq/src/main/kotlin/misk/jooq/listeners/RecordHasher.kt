package misk.jooq.listeners

/** Interface for computing and verifying MAC signatures for database records. */
interface RecordHasher {
  /**
   * Computes a MAC signature for the given data using the specified key.
   *
   * @param keyName The name of the key to use for signing
   * @param data The data to sign
   * @return The MAC signature as a byte array
   * @throws IllegalArgumentException if the key name is not found
   * @throws RuntimeException if signature computation fails
   */
  fun computeMac(keyName: String, data: ByteArray): ByteArray

  /**
   * Verifies a MAC signature for the given data using the specified key.
   *
   * @param keyName The name of the key to use for verification
   * @param providedMac The MAC to verify
   * @param data The data that was signed
   * @throws IllegalArgumentException if the key name is not found
   * @throws SecurityException if verification fails
   */
  fun verifyMac(keyName: String, providedMac: ByteArray, data: ByteArray)
}

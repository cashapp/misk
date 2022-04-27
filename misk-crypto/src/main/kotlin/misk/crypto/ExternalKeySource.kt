package misk.crypto

/**
 * Implement an ExternalKeySource to provide Tink keysets from an external location, such as
 * an S3 bucket or an NFS share.
 */
interface ExternalKeySource {
  /**
   * Check if a key alias exists in the key source.
   */
  fun keyExists(alias: KeyAlias): Boolean

  /**
   * Return a [Key] from an external key source.
   */
  fun getKey(alias: KeyAlias): Key?
}

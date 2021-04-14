package misk.crypto

import misk.environment.Env

/**
 * Bind this to an instance to control how external buckets are referenced.
 */
interface BucketNameSource {
  /**
   * Name of the bucket that stores keys
   */
  fun getBucketName(env: Env): String

  /**
   * The region that the bucket lives in.
   *
   * Returns null for same region as the service.
   */
  fun getBucketRegion(env: Env): String? = null
}

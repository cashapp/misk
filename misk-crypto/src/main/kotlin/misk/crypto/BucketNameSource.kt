package misk.crypto

import wisp.deployment.Deployment

/**
 * Bind this to an instance to control how external buckets are referenced.
 */
interface BucketNameSource {
  /**
   * Name of the bucket that stores keys
   */
  fun getBucketName(deployment: Deployment): String

  /**
   * The region that the bucket lives in.
   *
   * Returns null for same region as the service.
   */
  fun getBucketRegion(deployment: Deployment): String? = null
}

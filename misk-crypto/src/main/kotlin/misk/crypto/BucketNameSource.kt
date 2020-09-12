package misk.crypto

import misk.environment.Deployment

/**
 * Bind this to an instance to control how external buckets are referenced.
 */
interface BucketNameSource {
  fun getBucketName(deployment: Deployment): String
}

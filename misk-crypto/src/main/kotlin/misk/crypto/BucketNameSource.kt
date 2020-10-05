package misk.crypto

import misk.environment.Env

/**
 * Bind this to an instance to control how external buckets are referenced.
 */
interface BucketNameSource {
  fun getBucketName(env: Env): String
}

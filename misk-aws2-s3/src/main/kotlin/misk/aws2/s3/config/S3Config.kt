package misk.aws2.s3.config

import misk.config.Config

data class S3Config @JvmOverloads constructor(
  /** AWS Region of the s3 bucket, defaults to the current region. */
  val region: String? = null
) : Config

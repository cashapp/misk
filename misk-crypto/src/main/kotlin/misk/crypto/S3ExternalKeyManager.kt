package misk.crypto

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.google.crypto.tink.KeysetHandle
import com.google.inject.Inject
import misk.config.MiskConfig
import misk.environment.Deployment
import misk.logging.getLogger

/**
 * [S3ExternalKeyManager] implements an [ExternalKeyManager] that fetches Tink keysets from an S3
 * bucket. Keysets are indexed by an alias and a region, and are encrypted with a key in the KMS
 * using an envelope key encryption scheme. Each Keyset is protected by a KMS key in each service
 * region.
 *
 * For example, if we are to load a key aliased by "example_key" in the staging environment, we'd
 * expect the following layout:
 *
 * bucket
 *  ↳ example_key
 *    ↳ us-east-1
 *    ↳ us-west-2
 *
 *  And the metadata of the region-specified object will contain the KMS key arn that was used to
 *  protect it under the key x-amz-meta-kms-key-arn, and the type of the key under
 *  x-amz-meta-key-type. These keys are exposed in the S3 api without the `x-amz-meta-` prefix.
 *
 *  The envelope scheme itself is from misk-crypto and is defined in [KeyReader]
 *
 *  If a requested key alias does not exist, this will raise a [ExternalKeyManagerException]
 */
class S3ExternalKeyManager(
  private val env: Deployment,
  private val s3: AmazonS3,
  override val allKeyAliases: Map<KeyAlias, KeyType>
) : ExternalKeyManager {

  // By default, look for a bucket with the same name as the deployment environment.
  // Bind a new name source to give it a different name
  @Inject(optional = true)
  private var bucketNamer: BucketNameSource = object : BucketNameSource {
    override fun getBucketName(deployment: Deployment) = deployment.name.toLowerCase()
  }

  private val logger by lazy { getLogger<S3ExternalKeyManager>() }

  private val metadataKeyKmsArn = "kms-key-arn"

  private val metadataKeyKeyType = "key-type"

  private fun objectPath(alias: String) = "$alias/${s3.regionName.toLowerCase()}"

  private var keys: LinkedHashMap<String, Key> = linkedMapOf()

  private fun makeKmsUri(arn: String) = "aws-kms://$arn"

  init {
    allKeyAliases.forEach { (alias, type) ->
      try {
        val path = objectPath(alias)
        val obj = s3.getObject(bucketNamer.getBucketName(env), path)

        val kmsUri = makeKmsUri(obj.objectMetadata.getUserMetaDataOf(metadataKeyKmsArn))
        val keyTypeDescription = obj.objectMetadata.getUserMetaDataOf(metadataKeyKeyType)

        val keyType = KeyType.valueOf(keyTypeDescription.toUpperCase())
        if (keyType != type) {
          throw ExternalKeyManagerException("type provided does not match type of remote key")
        }

        val keyContents = obj.objectContent.readAllBytes().toString(Charsets.UTF_8)
        val key = Key(alias, keyType, MiskConfig.RealSecret(keyContents), kmsUri)

        keys[alias] = key

        logger.info { "registered external key $alias" }
      } catch (ex: AmazonS3Exception) {
        throw ExternalKeyManagerException("key alias not accessible: $alias ($ex)")
      }
    }
  }

  override fun getKeyByAlias(alias: KeyAlias) = keys[alias]

  override fun onKeyUpdated(cb: (KeyAlias, KeysetHandle) -> Unit) = false
}

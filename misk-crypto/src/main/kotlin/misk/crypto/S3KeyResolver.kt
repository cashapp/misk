package misk.crypto

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.google.inject.Inject
import misk.config.MiskConfig
import misk.environment.Env
import wisp.logging.getLogger

/**
 * [S3KeyResolver] implements an [KeyResolver] that fetches Tink keysets from an S3
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
class S3KeyResolver @Inject constructor(
  private val env: Env,

  private val defaultS3: AmazonS3,

  @ExternalDataKeys override val allKeyAliases: Map<KeyAlias, KeyType>,

  @Inject(optional = true)
  private val bucketNameSource: BucketNameSource = object : BucketNameSource {
    override fun getBucketName(env: Env) = env.name.toLowerCase()
  },

  // The data keys bucket might live in a different region than the region this service executes
  // in; if BucketNameSource provides a non-standard bucket region, we need to create a new S3
  // client for that bucket, and to do that we need credentials again.
  private val awsCredentials: AWSCredentialsProvider,
) : ExternalKeyResolver {

  private val s3: AmazonS3 = bucketNameSource.getBucketRegion(env)?.let { region ->
    logger.info("creating S3ExternalKeyManager S3 client for $region")
    AmazonS3ClientBuilder
      .standard()
      .withRegion(region)
      .withCredentials(awsCredentials)
      .build()
  } ?: defaultS3

  // N.B. The path we're using for the object is based on _our_ region, not where the bucket lives
  private fun objectPath(alias: String) = "$alias/${defaultS3.regionName.toLowerCase()}"

  private fun getRemoteKey(alias: KeyAlias, type: KeyType): Key {
    val path = objectPath(alias)
    val name = bucketNameSource.getBucketName(env)
    try {
      val obj = s3.getObject(name, path)

      val kmsArn = obj.objectMetadata.getUserMetaDataOf(METADATA_KEY_KMS_ARN)
      val keyTypeDescription = obj.objectMetadata.getUserMetaDataOf(METADATA_KEY_KEY_TYPE)

      val keyType = KeyType.valueOf(keyTypeDescription.toUpperCase())
      if (keyType != type) {
        throw ExternalKeyManagerException("type provided does not match type of remote key")
      }

      val keyContents = obj.objectContent.readAllBytes().toString(Charsets.UTF_8)
      return Key(alias, keyType, MiskConfig.RealSecret(keyContents), kmsArn)
    } catch (ex: AmazonS3Exception) {
      throw ExternalKeyManagerException("key alias not accessible: $alias (bucket '$name', $ex)")
    }
  }

  // Injector tests initialize key managers in non-native environments, so we delegate creation
  // until needed.
  private val keys: LinkedHashMap<String, Key> by lazy {
    val retrievedKeys = linkedMapOf<String, Key>()
    allKeyAliases.mapValues { (alias, type) ->
      logger.info { "registering external key: $alias" }
      getRemoteKey(alias, type)
    }.toMap(retrievedKeys)
    retrievedKeys
  }

  override fun getKeyByAlias(alias: KeyAlias) = keys[alias]

  companion object {
    private const val METADATA_KEY_KMS_ARN = "kms-key-arn"

    private const val METADATA_KEY_KEY_TYPE = "key-type"

    private val logger = getLogger<S3KeyResolver>()
  }

}

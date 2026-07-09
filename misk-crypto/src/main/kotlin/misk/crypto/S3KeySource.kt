package misk.crypto

import com.google.inject.Inject
import misk.config.MiskConfig
import misk.logging.getLogger
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception
import wisp.deployment.Deployment

/**
 * [S3KeySource] implements an [ExternalKeySource] that fetches Tink keysets from an S3 bucket. Keysets are indexed by
 * an alias and a region, and are encrypted with a key in the KMS using an envelope key encryption scheme. Each Keyset
 * is protected by a KMS key in each service region.
 *
 * For example, if we are to load a key aliased by "example_key" in the staging environment, we'd expect the following
 * layout:
 *
 * bucket ↳ example_key ↳ us-east-1 ↳ us-west-2
 *
 * And the metadata of the region-specified object will contain the KMS key arn that was used to protect it under the
 * key x-amz-meta-kms-key-arn, and the type of the key under x-amz-meta-key-type. These keys are exposed in the S3 api
 * without the `x-amz-meta-` prefix.
 *
 * The envelope scheme itself is from misk-crypto and is defined in [KeyReader]
 *
 * If a requested key alias does not exist, this will raise a [ExternalKeyManagerException]
 */
class S3KeySource
@Inject
constructor(
  private val deployment: Deployment,
  defaultS3: S3Client,
  @ExternalDataKeys val allKeyAliases: Map<KeyAlias, KeyType>,
  @Inject(optional = true)
  private val bucketNameSource: BucketNameSource =
    object : BucketNameSource {
      override fun getBucketName(deployment: Deployment) = deployment.mapToEnvironmentName()
    },

  // The data keys bucket might live in a different region than the region this service executes
  // in; if BucketNameSource provides a non-standard bucket region, we need to create a new S3
  // client for that bucket, and to do that we need credentials again.
  private val awsCredentials: AwsCredentialsProvider,
) : ExternalKeySource {

  private val defaultRegion: String = defaultS3.serviceClientConfiguration().region().id()

  private val s3: S3Client =
    bucketNameSource.getBucketRegion(deployment)?.let { region ->
      if (region != defaultRegion) {
        logger.info("creating S3KeySource S3 client for $region")
        S3Client.builder().region(Region.of(region)).credentialsProvider(awsCredentials).build()
      } else null
    } ?: defaultS3

  private val bucketRegion: String = bucketNameSource.getBucketRegion(deployment) ?: defaultRegion

  // N.B. The path we're using for the object is based on _our_ region, not where the bucket lives
  private fun objectPath(alias: String): String {
    val basename =
      if (allKeyAliases[alias] == KeyType.HYBRID_ENCRYPT) {
        "public"
      } else {
        bucketRegion.lowercase()
      }

    return "$alias/$basename"
  }

  override fun keyExists(alias: KeyAlias): Boolean {
    val path = objectPath(alias)
    val name = bucketNameSource.getBucketName(deployment)
    return try {
      s3.headObject(HeadObjectRequest.builder().bucket(name).key(path).build())
      true
    } catch (_: NoSuchKeyException) {
      false
    } catch (e: S3Exception) {
      // HeadObject returns a bodiless 404, which some SDK versions surface as a plain S3Exception.
      if (e.statusCode() == 404) false else throw e
    }
  }

  override fun getKey(alias: KeyAlias): Key {
    val path = objectPath(alias)
    val name = bucketNameSource.getBucketName(deployment)
    try {
      s3
        .getObject { it.bucket(name).key(path) }
        .use { obj ->
          val metadata = obj.response().metadata()
          val kmsArn = metadata[METADATA_KEY_KMS_ARN]
          val keyTypeDescription =
            metadata[METADATA_KEY_KEY_TYPE]
              ?: throw ExternalKeyManagerException("key alias missing $METADATA_KEY_KEY_TYPE metadata: $alias")

          val keyType = KeyType.valueOf(keyTypeDescription.uppercase())

          val keyContents = obj.readAllBytes().toString(Charsets.UTF_8)
          return Key(alias, keyType, MiskConfig.RealSecret(keyContents), kmsArn)
        }
    } catch (ex: AwsServiceException) {
      throw ExternalKeyManagerException("key alias not accessible: $alias (bucket '$name', $ex)")
    }
  }

  companion object {
    private const val METADATA_KEY_KMS_ARN = "kms-key-arn"

    private const val METADATA_KEY_KEY_TYPE = "key-type"

    private val logger = getLogger<S3KeySource>()
  }
}

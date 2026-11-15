package misk.aws2.s3

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import java.net.URI
import misk.containers.Composer
import misk.containers.Container
import misk.containers.ContainerUtil
import misk.logging.getLogger
import misk.testing.ExternalDependency
import misk.tokens.RealTokenGenerator
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.ListBucketsRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchBucketException

/**
 * Enhanced S3 test container with automatic bucket management and comprehensive cleanup.
 *
 * Features:
 * - Automatic bucket creation with unique names
 * - Comprehensive cleanup after each test
 * - Better health checking and startup
 * - Support for multiple test buckets
 * - Configurable port, region, and LocalStack image
 */
class DockerS3(
  private val port: Int = 4566,
  val region: Region = Region.US_EAST_1,
  private val localStackImage: String = "localstack/localstack:3.0",
  private val additionalEnvVars: Map<String, String> = emptyMap(),
  private val healthCheckTimeoutSeconds: Int = 30,
) : ExternalDependency {

  private val logger = getLogger<DockerS3>()
  private val tokenGenerator = RealTokenGenerator()
  private val containerId = tokenGenerator.generate("s3", 8)

  companion object {
    /** Default instance for backward compatibility and simple use cases. */
    val Default = DockerS3()
  }

  override fun beforeEach() {
    // Clean state for each test
    cleanupTestBuckets()
  }

  override fun afterEach() {
    // Comprehensive cleanup after each test
    cleanupAllBuckets()
  }

  private val composer =
    Composer(
      "e-s3",
      Container {
        val exposedClientPort = ExposedPort.tcp(port)
        withImage(localStackImage)
          .withName("s3-$containerId")
          .withEnv("SERVICES", "s3")
          .withEnv("DEBUG", "1")
          .apply {
            // Add any additional environment variables
            additionalEnvVars.forEach { (key, value) -> withEnv(key, value) }
          }
          .withExposedPorts(exposedClientPort)
          .withHostConfig(
            HostConfig.newHostConfig()
              .withPortBindings(Ports().apply { bind(exposedClientPort, Ports.Binding.bindPort(port)) })
          )
      },
    )

  val credentialsProvider = AwsCredentialsProvider {
    AwsBasicCredentials.builder().accessKeyId("test").secretAccessKey("test").accountId(null).providerName(null).build()
  }

  val endpointUri = URI.create("http://${ContainerUtil.dockerTargetOrLocalIp()}:$port")

  val client =
    S3Client.builder()
      .endpointOverride(endpointUri)
      .credentialsProvider(credentialsProvider)
      .region(region)
      .forcePathStyle(true)
      .build()

  // Test bucket management
  private val createdBuckets = mutableSetOf<String>()

  /**
   * Create a test bucket with a unique name or use a provided exact bucket name. Bucket will be automatically cleaned
   * up after tests.
   *
   * @param bucketName If this is the default "test-bucket", a unique name will be generated. Otherwise, the exact
   *   bucket name will be used as provided.
   */
  fun createTestBucket(bucketName: String = "test-bucket"): String {
    val finalBucketName =
      if (bucketName == "test-bucket") {
        // For the default value, generate a unique name
        "$bucketName-${tokenGenerator.generate(length = 8)}"
      } else {
        // Otherwise use the exact bucket name provided
        bucketName
      }

    try {
      // For LocalStack, don't specify location constraint for us-east-1
      client.createBucket(CreateBucketRequest.builder().bucket(finalBucketName).build())
      createdBuckets.add(finalBucketName)
      logger.info { "Created test bucket: $finalBucketName" }
    } catch (e: BucketAlreadyExistsException) {
      // Bucket already exists, that's fine
      createdBuckets.add(finalBucketName)
    }

    return finalBucketName
  }

  /** Check if a bucket exists. */
  fun bucketExists(bucketName: String): Boolean {
    return try {
      client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build())
      true
    } catch (e: NoSuchBucketException) {
      false
    }
  }

  private fun cleanupTestBuckets() {
    createdBuckets.forEach { bucketName ->
      try {
        clearBucketContents(bucketName)
      } catch (e: Exception) {
        logger.warn(e) { "Failed to clear bucket contents: $bucketName" }
      }
    }
  }

  private fun cleanupAllBuckets() {
    try {
      val buckets = client.listBuckets(ListBucketsRequest.builder().build())
      buckets.buckets().forEach { bucket ->
        try {
          clearBucketContents(bucket.name())
          client.deleteBucket(DeleteBucketRequest.builder().bucket(bucket.name()).build())
          logger.debug { "Deleted bucket: ${bucket.name()}" }
        } catch (e: Exception) {
          logger.warn(e) { "Failed to delete bucket: ${bucket.name()}" }
        }
      }
      createdBuckets.clear()
    } catch (e: Exception) {
      logger.warn(e) { "Failed to cleanup S3 buckets" }
    }
  }

  private fun clearBucketContents(bucketName: String) {
    try {
      val objects = client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucketName).build())

      objects.contents().forEach { obj ->
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(obj.key()).build())
      }
    } catch (e: Exception) {
      logger.warn(e) { "Failed to clear bucket contents: $bucketName" }
    }
  }

  override fun startup() {
    composer.start()

    // Enhanced health check - wait for S3 to be actually ready
    var attempts = 0
    val maxAttempts = healthCheckTimeoutSeconds

    while (attempts < maxAttempts) {
      try {
        client.listBuckets(ListBucketsRequest.builder().build())
        logger.info { "S3 is ready after $attempts attempts" }
        break
      } catch (e: Exception) {
        attempts++
        if (attempts >= maxAttempts) {
          throw RuntimeException("S3 failed to become ready after $maxAttempts attempts", e)
        }
        logger.debug { "S3 not ready yet (attempt $attempts/$maxAttempts): ${e.message}" }
        Thread.sleep(1000)
      }
    }
  }

  override fun shutdown() {
    cleanupAllBuckets()
    composer.stop()
  }
}

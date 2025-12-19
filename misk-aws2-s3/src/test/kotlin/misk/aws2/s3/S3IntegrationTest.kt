package misk.aws2.s3

import jakarta.inject.Inject
import kotlin.test.assertEquals
import misk.MiskTestingServiceModule
import misk.cloud.aws.AwsEnvironmentModule
import misk.cloud.aws.FakeAwsEnvironmentModule
import misk.inject.ReusableTestModule
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.MockTracingBackendModule
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest

@MiskTest(startService = false)
class S3IntegrationTest {
  @MiskTestModule val module = TestModule()

  @MiskExternalDependency val dockerS3 = DockerS3()

  @Inject private lateinit var s3Client: S3Client
  @Inject private lateinit var s3AsyncClient: S3AsyncClient

  @Test
  fun `can use sync S3 client`() {
    val bucketName = dockerS3.createTestBucket("sync-test")
    val key = "test-key"
    val content = "Hello, S3!"

    // Put object
    s3Client.putObject(PutObjectRequest.builder().bucket(bucketName).key(key).build(), content.toRequestBody())

    // Get object
    val response = s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(key).build())

    val retrievedContent = response.readAllBytes().toString(Charsets.UTF_8)
    assertEquals(content, retrievedContent)
    // No cleanup needed - DockerS3 handles it automatically
  }

  @Test
  fun `can use async S3 client`() {
    val bucketName = dockerS3.createTestBucket("async-test")
    val key = "test-async-key"
    val content = "Hello, async S3!"

    // Put object
    s3AsyncClient
      .putObject(PutObjectRequest.builder().bucket(bucketName).key(key).build(), AsyncRequestBody.fromString(content))
      .join()

    // Get object
    val response =
      s3AsyncClient
        .getObject(GetObjectRequest.builder().bucket(bucketName).key(key).build(), AsyncResponseTransformer.toBytes())
        .join()

    val retrievedContent = response.asByteArray().toString(Charsets.UTF_8)
    assertEquals(content, retrievedContent)
    // No cleanup needed - DockerS3 handles it automatically
  }

  @Test
  fun `can use exact bucket names`() {
    // Create a test bucket with an exact name
    val exactBucketName = "my-exact-bucket-name-2025"
    val bucketName = dockerS3.createTestBucket(exactBucketName)

    // Verify the bucket name is exactly what we specified
    assertEquals(exactBucketName, bucketName, "Bucket name should be exactly what was provided")

    // Test basic S3 operations to ensure the custom bucket naming doesn't break functionality
    val key = "test-key"
    val content = "Testing exact bucket names"

    // Put object
    dockerS3.client.putObject(
      PutObjectRequest.builder().bucket(bucketName).key(key).build(),
      RequestBody.fromString(content),
    )

    // Get object
    val response = dockerS3.client.getObject(GetObjectRequest.builder().bucket(bucketName).key(key).build())

    val retrievedContent = response.readAllBytes().toString(Charsets.UTF_8)
    assertEquals(content, retrievedContent)
  }

  class TestModule : ReusableTestModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(MockTracingBackendModule())
      install(AwsEnvironmentModule())
      install(FakeAwsEnvironmentModule())
      install(S3TestModule())
    }
  }
}

// Extension function for Kotlin-style builder
private fun String.toRequestBody(): RequestBody = RequestBody.fromString(this)

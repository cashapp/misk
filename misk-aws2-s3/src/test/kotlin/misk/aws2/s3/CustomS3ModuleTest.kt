package misk.aws2.s3

import jakarta.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import misk.MiskTestingServiceModule
import misk.aws2.s3.config.S3Config
import misk.cloud.aws.AwsEnvironmentModule
import misk.cloud.aws.FakeAwsEnvironmentModule
import misk.inject.KAbstractModule
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.MockTracingBackendModule
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest

/**
 * Custom S3 module that demonstrates client configuration via lambda functions. This shows how to configure LocalStack
 * endpoint and path style via lambdas.
 */
class CustomS3TestModule : KAbstractModule() {
  override fun configure() {
    // Install required dependencies for S3Module
    install(MiskTestingServiceModule())
    install(MockTracingBackendModule())
    install(AwsEnvironmentModule())
    install(FakeAwsEnvironmentModule())

    // Create a DockerS3 instance for this test
    val dockerS3 = DockerS3()
    bind<AwsCredentialsProvider>().toInstance(dockerS3.credentialsProvider)
    bind<Region>().toInstance(dockerS3.region)

    // Lambda-only configuration approach
    install(
      S3Module(
        config = S3Config(region = "us-east-1"),
        configureSyncClient = {
          endpointOverride(java.net.URI.create("http://localhost:4566"))
          forcePathStyle(true)
        },
        configureAsyncClient = {
          endpointOverride(java.net.URI.create("http://localhost:4566"))
          forcePathStyle(true)
        },
      )
    )
  }
}

@MiskTest(startService = false)
class CustomS3ModuleTest {
  @MiskTestModule val module = CustomS3TestModule()

  @MiskExternalDependency val dockerS3 = DockerS3()

  @Inject private lateinit var s3Client: S3Client
  @Inject private lateinit var s3AsyncClient: S3AsyncClient

  @Test
  fun `custom S3 module provides working clients`() {
    assertNotNull(s3Client)
    assertNotNull(s3AsyncClient)
  }

  @Test
  fun `custom S3 module can perform S3 operations`() {
    val bucketName = dockerS3.createTestBucket("custom-test")
    val key = "custom-test-key"
    val content = "Hello from custom S3 module!"

    // Put object using custom configured client
    s3Client.putObject(PutObjectRequest.builder().bucket(bucketName).key(key).build(), RequestBody.fromString(content))

    // Get object using custom configured client
    val response = s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(key).build())

    val retrievedContent = response.readAllBytes().toString(Charsets.UTF_8)
    assertEquals(content, retrievedContent)
  }

  @Test
  fun `custom S3 module works with async client`() {
    val bucketName = dockerS3.createTestBucket("custom-async-test")
    val key = "custom-async-key"
    val content = "Hello from custom async S3!"

    // Put object using async client
    s3AsyncClient
      .putObject(PutObjectRequest.builder().bucket(bucketName).key(key).build(), AsyncRequestBody.fromString(content))
      .join()

    // Get object using async client
    val response =
      s3AsyncClient
        .getObject(GetObjectRequest.builder().bucket(bucketName).key(key).build(), AsyncResponseTransformer.toBytes())
        .join()

    val retrievedContent = response.asByteArray().toString(Charsets.UTF_8)
    assertEquals(content, retrievedContent)
  }

  @Test
  fun `lambda configuration is applied correctly`() {
    // This test verifies that the lambda-based configuration with endpointOverride
    // and forcePathStyle is properly applied to both sync and async clients by
    // successfully performing operations against LocalStack
    val bucketName = dockerS3.createTestBucket("lambda-config-test")
    val key = "lambda-config-test-key"
    val content = "Lambda configuration test successful!"

    // Test sync client
    s3Client.putObject(PutObjectRequest.builder().bucket(bucketName).key(key).build(), RequestBody.fromString(content))

    val syncResponse = s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(key).build())
    assertEquals(content, syncResponse.readAllBytes().toString(Charsets.UTF_8))

    // Test async client with different content
    val asyncContent = "Async lambda configuration test!"
    val asyncKey = "async-lambda-config-key"

    s3AsyncClient
      .putObject(
        PutObjectRequest.builder().bucket(bucketName).key(asyncKey).build(),
        AsyncRequestBody.fromString(asyncContent),
      )
      .join()

    val asyncResponse =
      s3AsyncClient
        .getObject(
          GetObjectRequest.builder().bucket(bucketName).key(asyncKey).build(),
          AsyncResponseTransformer.toBytes(),
        )
        .join()

    assertEquals(asyncContent, asyncResponse.asByteArray().toString(Charsets.UTF_8))
  }
}

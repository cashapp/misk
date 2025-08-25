# misk-aws2-s3

AWS SDK 2.x S3 integration for Misk applications.

## Quick Start

### Add Dependency
```kotlin
implementation("app.cash.misk:misk-aws2-s3")

// For test fixtures to use in testing
testImplementation(testFixtures("app.cash.misk:misk-aws2-s3"))
```

### Install Module
```kotlin
class MyServiceModule : KAbstractModule() {
  override fun configure() {
    install(S3Module(S3Config(region = "us-east-1")))
  }
}
```

### Use S3 Clients
```kotlin
class MyFileUploaderService @Inject constructor(
  private val s3: S3Client,
  private val s3Async: S3AsyncClient
) {
  fun uploadFile(bucket: String, key: String, content: String) {
    s3.putObject(
      PutObjectRequest.builder().bucket(bucket).key(key).build(),
      RequestBody.fromString(content)
    )
  }
}
```

## Configuration

The provided `S3Module` accepts an `S3Config` to allow overriding of AWS Region and other properties.

Most configuration can be done though by passing in a builder lambda which can handle all custom configuration of the sync or async AWS S3 clients which the module builds and provides to injected callsites.

## Testing

### Simple Integration Tests
Use the provided `S3TestModule` for easy LocalStack integration:

```kotlin
@MiskTest(startService = false)
class S3IntegrationTest {
  @MiskTestModule
  val module = S3TestModule()

  @MiskExternalDependency
  val dockerS3 = DockerS3

  @Inject private lateinit var s3Client: S3Client

  @Test
  fun `test S3 operations`() {
    val bucketName = dockerS3.createTestBucket("my-test")
    
    s3Client.putObject(
      PutObjectRequest.builder().bucket(bucketName).key("test").build(),
      RequestBody.fromString("content")
    )
    
    val response = s3Client.getObject(
      GetObjectRequest.builder().bucket(bucketName).key("test").build()
    )
    
    assertEquals("content", response.readAllBytes().toString(Charsets.UTF_8))
    // No cleanup needed - DockerS3 handles it automatically
  }
}
```

### Custom Test Configuration
For more control, you can use S3Config to customize the client:

```kotlin
class CustomS3TestModule : KAbstractModule() {
  override fun configure() {
    install(S3Module(S3Config(
      region = "us-east-1",
      endpointOverride = "http://localhost:4566",
      forcePathStyle = true
    )))
  }
}
```

### DockerS3 Test Container Features
- **Automatic bucket creation** with unique names via `dockerS3.createTestBucket()`
- **Comprehensive cleanup** after each test - no manual cleanup needed
- **LocalStack integration** with proper health checks
- **Test isolation** - each test gets clean state

## Migration from misk-aws

| Old (misk-aws) | New (misk-aws2-s3) |
|----------------|---------------------|
| `install(RealS3Module())` | `install(S3Module(S3Config("us-east-1")))` |
| `@Inject lateinit var s3: AmazonS3` | `@Inject lateinit var s3: S3Client` |
| `s3.putObject(bucket, key, content)` | `s3.putObject(PutObjectRequest.builder()...build(), RequestBody.fromString(content))` |

After good internal discussion, I agree that exposing the S3 client directly makes more sense since it would be difficult to get the abstraction perfectly right such that it's actually useful for teams.

## Key Features

- **Simple Configuration**: Supports region, endpoint override, and path style configuration
- **LocalStack Support**: Easy configuration for local development with `endpointOverride` and `forcePathStyle`
- **Sync & Async Clients**: Both `S3Client` and `S3AsyncClient` available
- **Enhanced Testing**: `S3TestModule` and `DockerS3` with automatic cleanup
- **AWS SDK 2.x**: Modern, performant AWS SDK
- **Misk Integration**: Follows standard Misk patterns

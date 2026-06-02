package misk.aws2.s3

import misk.aws2.s3.config.S3Config
import misk.inject.ReusableTestModule
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

class S3TestModule(private val dockerS3: DockerS3 = DockerS3.Default) : ReusableTestModule() {

  override fun configure() {
    // Only bind S3-specific dependencies - downstream services should provide common test modules
    bind<AwsCredentialsProvider>().toInstance(dockerS3.credentialsProvider)

    install(
      S3Module(
        config = S3Config(region = dockerS3.region.id()),
        configureSyncClient = {
          endpointOverride(dockerS3.endpointUri)
          forcePathStyle(true)
        },
        configureAsyncClient = {
          endpointOverride(dockerS3.endpointUri)
          forcePathStyle(true)
        },
      )
    )
  }
}

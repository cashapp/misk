package misk.aws2.s3

import com.google.inject.Provides
import misk.aws2.s3.config.S3Config
import misk.cloud.aws.AwsRegion
import misk.inject.KAbstractModule
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3ClientBuilder
import jakarta.inject.Singleton

open class S3Module @JvmOverloads constructor(
  private val config: S3Config = S3Config(),
  private val configureSyncClient: S3ClientBuilder.() -> Unit = {},
  private val configureAsyncClient: S3AsyncClientBuilder.() -> Unit = {}
) : KAbstractModule() {
  
  override fun configure() {
    requireBinding<AwsCredentialsProvider>()
    requireBinding<AwsRegion>()
  }

  @Provides @Singleton
  fun s3Client(
    credentialsProvider: AwsCredentialsProvider,
    awsRegion: AwsRegion
  ): S3Client {
    val region = config.region ?: awsRegion.name
    
    val builder = S3Client.builder()
      .credentialsProvider(credentialsProvider)
      .region(Region.of(region))
    
    // Apply custom configuration via lambda
    builder.configureSyncClient()
    return builder.build()
  }

  @Provides @Singleton
  fun s3AsyncClient(
    credentialsProvider: AwsCredentialsProvider,
    awsRegion: AwsRegion
  ): S3AsyncClient {
    val region = config.region ?: awsRegion.name
    
    val builder = S3AsyncClient.builder()
      .credentialsProvider(credentialsProvider)
      .region(Region.of(region))
    
    // Apply custom configuration via lambda
    builder.configureAsyncClient()
    return builder.build()
  }
}

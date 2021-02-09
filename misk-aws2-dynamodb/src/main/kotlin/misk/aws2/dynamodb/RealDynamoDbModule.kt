package misk.aws2.dynamodb

import com.google.inject.Provides
import misk.cloud.aws.AwsRegion
import misk.inject.KAbstractModule
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import javax.inject.Singleton

/**
 * Install this module to have access to a DynamoDbClient.
 */
class RealDynamoDbModule constructor(
  private val clientOverrideConfig: ClientOverrideConfiguration =
      ClientOverrideConfiguration.builder().build()
) : KAbstractModule() {
  override fun configure() {
    requireBinding<AwsCredentialsProvider>()
    requireBinding<AwsRegion>()
  }

  @Provides @Singleton
  fun providesDynamoDbClient(
    awsRegion: AwsRegion,
    awsCredentialsProvider: AwsCredentialsProvider
  ): DynamoDbClient {
    return DynamoDbClient.builder()
        .region(Region.of(awsRegion.name))
        .credentialsProvider(awsCredentialsProvider)
        .overrideConfiguration(clientOverrideConfig)
        .build()
  }
}

package misk.aws2.dynamodb

import com.google.inject.Provides
import javax.inject.Singleton
import misk.cloud.aws.AwsRegion
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

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
    multibind<HealthCheck>().to<DynamoDbHealthCheck>()
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

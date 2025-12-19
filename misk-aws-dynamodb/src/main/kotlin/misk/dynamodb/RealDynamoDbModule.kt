package misk.dynamodb

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreamsClientBuilder
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import com.google.inject.Provides
import jakarta.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import misk.ReadyService
import misk.ServiceModule
import misk.cloud.aws.AwsRegion
import misk.exceptions.dynamodb.DynamoDbExceptionMapperModule
import misk.inject.KAbstractModule

/**
 * Install this module to have access to an AmazonDynamoDB client. This can be used to create a DynamoDbMapper for
 * querying of a DynamoDb table.
 *
 * @param requiredTableTypes a list of mapper classes annotated [DynamoDBTable].
 */
open class RealDynamoDbModule
@JvmOverloads
constructor(
  private val clientConfig: ClientConfiguration = ClientConfiguration(),
  vararg requiredTableTypes: KClass<*>,
) : KAbstractModule() {
  private val requiredTables: List<RequiredDynamoDbTable> =
    requiredTableTypes.map {
      val annotation = it.findAnnotation<DynamoDBTable>() ?: throw IllegalArgumentException("no @DynamoDBTable on $it")
      RequiredDynamoDbTable(annotation.tableName)
    }

  override fun configure() {
    requireBinding<AWSCredentialsProvider>()
    requireBinding<AwsRegion>()
    bind<DynamoDbService>().to<RealDynamoDbService>()
    install(ServiceModule<DynamoDbService>().enhancedBy<ReadyService>())
    install(DynamoDbExceptionMapperModule())
  }

  @Provides @Singleton fun provideRequiredTables(): List<RequiredDynamoDbTable> = requiredTables

  @Provides
  @Singleton
  fun providesAmazonDynamoDB(awsRegion: AwsRegion, awsCredentialsProvider: AWSCredentialsProvider): AmazonDynamoDB {
    val builder =
      AmazonDynamoDBClientBuilder.standard()
        .withRegion(awsRegion.name)
        .withCredentials(awsCredentialsProvider)
        .withClientConfiguration(clientConfig)
    configureClient(builder)
    return builder.build()
  }

  open fun configureClient(builder: AmazonDynamoDBClientBuilder) {}

  @Provides
  @Singleton
  fun providesAmazonDynamoDBStreams(
    awsRegion: AwsRegion,
    awsCredentialsProvider: AWSCredentialsProvider,
  ): AmazonDynamoDBStreams {
    val builder =
      AmazonDynamoDBStreamsClientBuilder.standard()
        .withRegion(awsRegion.name)
        .withCredentials(awsCredentialsProvider)
        .withClientConfiguration(clientConfig)
    configureStreamsClient(builder)
    return builder.build()
  }

  open fun configureStreamsClient(builder: AmazonDynamoDBStreamsClientBuilder) {}
}

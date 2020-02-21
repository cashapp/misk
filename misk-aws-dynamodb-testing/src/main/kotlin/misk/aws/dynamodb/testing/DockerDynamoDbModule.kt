package misk.aws.dynamodb.testing

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput
import com.amazonaws.services.dynamodbv2.model.ResourceInUseException
import com.google.inject.Provides
import misk.cloud.aws.FakeAwsEnvironmentModule
import misk.inject.KAbstractModule
import javax.inject.Singleton
import kotlin.reflect.KClass

class DockerDynamoDbModule(
  private val entities: List<Pair<String, KClass<*>>>
) : KAbstractModule() {
  override fun configure() {
    install(FakeAwsEnvironmentModule())
  }

  @Provides @Singleton
  fun providesAmazonDynamoDB(): AmazonDynamoDB {
    val dynamoDb = AmazonDynamoDBClientBuilder
        .standard()
        .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials("key", "secret")))
        .withEndpointConfiguration(
            AwsClientBuilder.EndpointConfiguration(
                "http://localhost:8000", // TODO: inject from config
                Regions.US_WEST_1.toString()
            )
        )
        .build()

    entities.forEach {
      createTableForEntity(
          dynamoDb,
          it.second,
          it.first
      )
    }

    return dynamoDb
  }

  companion object {
    private fun createTableForEntity(
      amazonDynamoDB: AmazonDynamoDB,
      entity: KClass<*>,
      tableName: String
    ) {
      val tableRequest = DynamoDBMapper(amazonDynamoDB)
          .generateCreateTableRequest(entity.java)
          .withTableName(tableName)
          .withProvisionedThroughput(ProvisionedThroughput(1L, 1L))

      try {
        DynamoDB(amazonDynamoDB).createTable(tableRequest).waitForActive()
      } catch (e: ResourceInUseException) {
        // nothing
      }
    }
  }
}

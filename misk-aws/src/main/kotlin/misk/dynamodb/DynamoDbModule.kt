package misk.dynamodb

import misk.environment.Environment
import misk.inject.KAbstractModule
import kotlin.reflect.KClass

/*
 * Module for DynamoDb. The entities are a list of pairs, where the string is
 * the Dynamo table name, and the class is the entity class it represents.
 */
class DynamoDbModule(
  private val environment: Environment,
  private val entities: List<Pair<String, KClass<*>>>
) : KAbstractModule() {

  override fun configure() {
    when (environment) {
      Environment.PRODUCTION, Environment.STAGING -> {
        install(RealDynamoDbModule())
      }
      Environment.DEVELOPMENT, Environment.TESTING -> {
        install(FakeDynamoDbModule(entities))
      }
    }
  }
}

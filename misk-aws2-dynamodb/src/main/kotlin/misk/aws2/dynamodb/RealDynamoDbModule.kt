package misk.aws2.dynamodb

import com.google.inject.Injector
import com.google.inject.Provider
import jakarta.inject.Inject
import java.net.URI
import kotlin.reflect.KClass
import misk.ReadyService
import misk.ServiceModule
import misk.cloud.aws.AwsRegion
import misk.exceptions.dynamodb.DynamoDbExceptionMapperModule
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClientBuilder

/**
 * Install this module to have access to a DynamoDbClient.
 *
 * This module supports multiple installations with different qualifiers:
 * ```
 * install(RealDynamoDbModule(
 *   qualifier = PrimaryDynamoDb::class,
 *   clientOverrideConfig = primaryConfig
 * ))
 * install(RealDynamoDbModule(
 *   qualifier = SecondaryDynamoDb::class,
 *   clientOverrideConfig = secondaryConfig
 * ))
 *
 * // Usage:
 * class MyService @Inject constructor(
 *   @PrimaryDynamoDb val primaryDb: DynamoDbClient,
 *   @SecondaryDynamoDb val secondaryDb: DynamoDbClient
 * )
 * ```
 */
open class RealDynamoDbModule
@JvmOverloads
constructor(
  private val qualifier: KClass<out Annotation>? = null,
  private val clientOverrideConfig: ClientOverrideConfiguration = ClientOverrideConfiguration.builder().build(),
  private val requiredTables: List<RequiredDynamoDbTable> = listOf(),
  private val endpointOverride: URI? = null,
) : KAbstractModule() {
  // Backward-compatible constructor (unqualified)
  @JvmOverloads
  constructor(
    clientOverrideConfig: ClientOverrideConfiguration,
    requiredTables: List<RequiredDynamoDbTable> = listOf(),
    endpointOverride: URI? = null,
  ) : this(null, clientOverrideConfig, requiredTables, endpointOverride)

  override fun configure() {
    requireBinding<AwsCredentialsProvider>()
    requireBinding<AwsRegion>()

    bind(keyOf<DynamoDbClient>(qualifier))
      .toProvider(
        object : Provider<DynamoDbClient> {
          @Inject lateinit var awsRegion: AwsRegion
          @Inject lateinit var awsCredentialsProvider: AwsCredentialsProvider

          override fun get(): DynamoDbClient = createDynamoDbClient(awsRegion, awsCredentialsProvider)
        }
      )
      .asSingleton()

    bind(keyOf<DynamoDbStreamsClient>(qualifier))
      .toProvider(
        object : Provider<DynamoDbStreamsClient> {
          @Inject lateinit var awsRegion: AwsRegion
          @Inject lateinit var awsCredentialsProvider: AwsCredentialsProvider

          override fun get(): DynamoDbStreamsClient = createDynamoDbStreamsClient(awsRegion, awsCredentialsProvider)
        }
      )
      .asSingleton()

    bind(keyOf<List<RequiredDynamoDbTable>>(qualifier)).toInstance(requiredTables)

    bind(keyOf<RealDynamoDbService>(qualifier))
      .toProvider(
        object : Provider<RealDynamoDbService> {
          @Inject lateinit var injector: Injector

          override fun get(): RealDynamoDbService =
            RealDynamoDbService(injector = injector, requiredTables = requiredTables, qualifier = qualifier)
        }
      )
      .asSingleton()

    bind(keyOf<DynamoDbService>(qualifier)).to(keyOf<RealDynamoDbService>(qualifier))
    install(ServiceModule<DynamoDbService>(qualifier).enhancedBy<ReadyService>())

    install(DynamoDbExceptionMapperModule())
  }

  private fun createDynamoDbClient(
    awsRegion: AwsRegion,
    awsCredentialsProvider: AwsCredentialsProvider,
  ): DynamoDbClient {
    val builder =
      DynamoDbClient.builder()
        .region(Region.of(awsRegion.name))
        .credentialsProvider(awsCredentialsProvider)
        .overrideConfiguration(clientOverrideConfig)
    if (endpointOverride != null) {
      builder.endpointOverride(endpointOverride)
    }
    configureClient(builder)
    return builder.build()
  }

  private fun createDynamoDbStreamsClient(
    awsRegion: AwsRegion,
    awsCredentialsProvider: AwsCredentialsProvider,
  ): DynamoDbStreamsClient {
    val builder =
      DynamoDbStreamsClient.builder()
        .region(Region.of(awsRegion.name))
        .credentialsProvider(awsCredentialsProvider)
        .overrideConfiguration(clientOverrideConfig)
    if (endpointOverride != null) {
      builder.endpointOverride(endpointOverride)
    }
    configureClient(builder)
    return builder.build()
  }

  open fun configureClient(builder: DynamoDbClientBuilder) {}

  open fun configureClient(builder: DynamoDbStreamsClientBuilder) {}
}

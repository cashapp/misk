package misk.crypto

import com.google.crypto.tink.KmsClient
import com.google.crypto.tink.integration.gcpkms.GcpKmsClient
import com.google.inject.Provides
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import misk.inject.KAbstractModule
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider

/**
 * AWS specific KMS client module, backed by the AWS SDK v2.
 *
 * By default the KMS client is initialized from the AWS default credentials and region provider chains. Pass a
 * [credentialsProfile] to use a named profile from the AWS credentials file, or bind your own
 * [software.amazon.awssdk.services.kms.KmsClient] and use [tinkKmsClient] instead.
 *
 * The Tink [KmsClient] this module provides is ciphertext-compatible with the previous tink-awskms (AWS SDK v1) based
 * implementation; see [Aws2KmsClient].
 */
class AwsKmsClientModule @JvmOverloads constructor(private val credentialsProfile: String? = null) : KAbstractModule() {
  @Provides
  @Singleton
  fun getKmsClient(): KmsClient {
    val builder = software.amazon.awssdk.services.kms.KmsClient.builder()
    credentialsProfile?.let { builder.credentialsProvider(ProfileCredentialsProvider.create(it)) }
    return Aws2KmsClient(builder.build())
  }
}

/**
 * Binds a Tink [KmsClient] backed by an existing [software.amazon.awssdk.services.kms.KmsClient] binding. Use this
 * instead of [AwsKmsClientModule] when your application configures its own AWS SDK v2 KMS client (credentials, region,
 * endpoint overrides, etc).
 */
class Aws2KmsClientModule : KAbstractModule() {
  override fun configure() {
    requireBinding<software.amazon.awssdk.services.kms.KmsClient>()
  }

  @Provides
  @Singleton
  fun tinkKmsClient(kms: software.amazon.awssdk.services.kms.KmsClient): KmsClient = Aws2KmsClient(kms)
}

/**
 * GCP specific KMS client module. Uses a file path to a JSON credentials file to initialize the client.
 * * If no file is provided, tries to initialize the client using the default credentials path as specified in
 *   [GcpKmsClient.withDefaultCredentials]
 */
class GcpKmsClientModule @JvmOverloads constructor(private val credentialsPath: String? = null) : KAbstractModule() {
  @Provides
  @Singleton
  fun getKmsClient(): KmsClient =
    credentialsPath?.let { GcpKmsClient().withCredentials(it) } ?: GcpKmsClient().withDefaultCredentials()
}

/**
 * This annotation is used to specify which [software.amazon.awssdk.services.kms.KmsClient] instance should be used by
 * misk to construct a [KmsClient] and communicate with the KMS service
 */
@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class MiskAWSKMS

/** This annotation is used to specify the [KmsClient] that's being used by misk to load encryption keys */
@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class MiskKmsClient

package misk.crypto

import com.google.crypto.tink.KmsClient
import com.google.crypto.tink.integration.awskms.AwsKmsClient
import com.google.crypto.tink.integration.gcpkms.GcpKmsClient
import com.google.inject.Provides
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import misk.inject.KAbstractModule

/**
 * AWS specific KMS client module. Currently uses a file path to a credentials file to initialize the client. If no file
 * is provided, tries to initialize the client using the default credentials path as specified in
 * [AwsKmsClient.withDefaultCredentials].
 *
 * As of tink-awskms 2.x (built on the AWS SDK v2), the credentials file uses the standard AWS profile format
 * (`~/.aws/credentials` style ini file) rather than the legacy tink `.cred` JSON format.
 */
class AwsKmsClientModule @JvmOverloads constructor(private val credentialsPath: String? = null) : KAbstractModule() {
  @Provides
  @Singleton
  fun getKmsClient(): KmsClient =
    credentialsPath?.let { AwsKmsClient().withCredentials(it) } ?: AwsKmsClient().withDefaultCredentials()
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

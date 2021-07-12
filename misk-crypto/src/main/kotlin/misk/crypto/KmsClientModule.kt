package misk.crypto

import com.amazonaws.services.kms.AWSKMS
import com.amazonaws.services.kms.model.DescribeKeyRequest
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KmsClient
import com.google.crypto.tink.integration.awskms.AwsKmsAead
import com.google.crypto.tink.integration.gcpkms.GcpKmsClient
import com.google.crypto.tink.subtle.Validators
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.inject.KAbstractModule
import javax.inject.Qualifier

/**
 * AWS specific KMS client module.
 *
 * This module provides the [KmsClient] that'll be used by misk to decrypt keysets.
 * In order to initialize a client, make sure your app binds a [AWSKMS] object annotated with [MiskAWSKMS]
 */
class AwsKmsClientModule : KAbstractModule() {

  // TODO: Allow initializing an AwsKmsClient with a credentials provider and client configuration
  // once tink supports it: https://github.com/google/tink/pull/184
  @Provides @Singleton @MiskKmsClient
  fun provideKmsClient(@MiskAWSKMS awskms: AWSKMS): KmsClient = AwsKmsClient(awskms)

  /**
   * An implementation of [KmsClient] that uses an already existing [AWSKMS]
   * See [com.google.crypto.tink.integration.awskms.AwsKmsClient] for more details.
   *
   * The original [AwsKmsClient] can only be initialized by supplying
   * a [com.amazonaws.auth.AWSCredentialsProvider] which can be problematic in environments that
   * need to be able to further configure the client object, e.g. with proxy configurations
   */
  private class AwsKmsClient(private val kmsClient: AWSKMS) : KmsClient {

    private companion object {
      val PREFIX = com.google.crypto.tink.integration.awskms.AwsKmsClient.PREFIX
    }

    override fun getAead(keyUri: String?): Aead {
      val keyArn = "aws-kms://" +
        kmsClient.describeKey(DescribeKeyRequest().withKeyId(keyUri)).keyMetadata.arn
      return AwsKmsAead(kmsClient, Validators.validateKmsKeyUriAndRemovePrefix(PREFIX, keyArn))
    }

    override fun doesSupport(keyUri: String?): Boolean {
      return keyUri!!.toLowerCase().startsWith(PREFIX)
    }

    /**
     * Do not use.
     * If you need to initialize a [KmsClient] with a static credentials file please use
     * [com.google.crypto.tink.integration.awskms.AwsKmsClient]
     */
    override fun withCredentials(credentialPath: String?): KmsClient {
      throw UnsupportedOperationException("Not implemented")
    }

    /**
     * Do not use.
     * If you need to initialize a [KmsClient] using the default AWS credentials lookup path please use
     * [com.google.crypto.tink.integration.awskms.AwsKmsClient]
     */
    override fun withDefaultCredentials(): KmsClient {
      throw UnsupportedOperationException("Not implemented")
    }
  }
}

/**
 * GCP specific KMS client module.
 * Uses a file path to a JSON credentials file to initialize the client.
 * * If no file is provided, tries to initialize the client using the default
 * credentials path as specified in [GcpKmsClient.withDefaultCredentials]
 */
class GcpKmsClientModule(private val credentialsPath: String? = null) : KAbstractModule() {
  @Provides @Singleton
  fun getKmsClient(): KmsClient = credentialsPath?.let { GcpKmsClient().withCredentials(it) }
    ?: GcpKmsClient().withDefaultCredentials()
}

/**
 * This annotation is used to specify which [com.amazonaws.services.kms.AWSKMS]
 * instance should be used by misk to construct a [KmsClient] and communicate with the KMS service
 */
@Qualifier
@Target(
  AnnotationTarget.FIELD,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.VALUE_PARAMETER
)
@Retention(AnnotationRetention.RUNTIME)
annotation class MiskAWSKMS

/**
 * This annotation is used to specify the [KmsClient] that's
 * being used by misk to load encryption keys
 */
@Qualifier
@Target(
  AnnotationTarget.FIELD,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.VALUE_PARAMETER
)
@Retention(AnnotationRetention.RUNTIME)
annotation class MiskKmsClient

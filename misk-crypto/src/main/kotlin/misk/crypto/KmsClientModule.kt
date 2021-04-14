package misk.crypto

import com.amazonaws.auth.AWSCredentialsProvider
import com.google.crypto.tink.KmsClient
import com.google.crypto.tink.integration.awskms.AwsKmsClient
import com.google.crypto.tink.integration.gcpkms.GcpKmsClient
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.inject.KAbstractModule

/**
 * AWS specific KMS client module.
 *
 * This module provides a [AwsKmsClient].
 * It can be initialized with either a [AWSCredentialsProvider],
 * a string containing the path to AWS credentials, or with the default settings
 * as specified in [AwsKmsClient.withDefaultCredentials].
 *
 * See [AWS credentials](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html)
 * for more information about supplying credentials to AWS.
 */
class AwsKmsClientModule(
  private val credentialsProvider: AWSCredentialsProvider? = null,
  private val credentialsPath: String? = null
) : KAbstractModule() {
  @Provides @Singleton
  fun getKmsClient(): KmsClient {
    if (credentialsProvider != null) {
      return AwsKmsClient().withCredentialsProvider(credentialsProvider)
    }
    return credentialsPath?.let { AwsKmsClient().withCredentials(it) }
      ?: AwsKmsClient().withDefaultCredentials()
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

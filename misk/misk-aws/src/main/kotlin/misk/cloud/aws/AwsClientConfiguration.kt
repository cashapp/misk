package misk.cloud.aws

import com.amazonaws.ClientConfiguration
import javax.inject.Singleton

/**
 * Misk clients can optionally provide their own [ClientConfiguration] for Misk to use when
 * it builds AWS clients. A common example of when you might want an override is to provide
 * proxy details to use when communicating to AWS.
 */
@Singleton class AwsClientConfiguration {
  @com.google.inject.Inject(optional = true)
  var clientConfiguration: ClientConfiguration? = null
}

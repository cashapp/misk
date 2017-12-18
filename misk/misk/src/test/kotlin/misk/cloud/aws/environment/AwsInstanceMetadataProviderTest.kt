package misk.cloud.aws.environment

import misk.cloud.aws.environment.AwsInstanceMetadataProvider.Companion.AWS_INSTANCE_METADATA_PATH
import misk.testing.okhttp.RoutingDispatcher
import misk.testing.okhttp.path
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AwsInstanceMetadataProviderTest {
  @Test
  fun resolvesMetadata() {
    val routes = RoutingDispatcher {
      when (it.requestUrl.path) {
        "$AWS_INSTANCE_METADATA_PATH/instance-id" ->
          MockResponse().setBody("i-1234567890abcdef0")
        "$AWS_INSTANCE_METADATA_PATH/placement/availability-zone" ->
          MockResponse().setBody("us-west-2c")
        else -> MockResponse().setResponseCode(404)
      }
    }

    val server = MockWebServer()
    server.setDispatcher(routes)
    server.start()

    val provider = AwsInstanceMetadataProvider("http://${server.hostName}:${server.port}")

    try {
      val instanceMetadata = provider.get()
      assertThat(instanceMetadata.instanceName).isEqualTo("i-1234567890abcdef0")
      assertThat(instanceMetadata.zone).isEqualTo("us-west-2c")
    } finally {
      server.shutdown()
    }
  }
}

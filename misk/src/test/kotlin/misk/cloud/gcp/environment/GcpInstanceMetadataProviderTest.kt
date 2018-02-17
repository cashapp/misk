package misk.cloud.gcp.environment

import misk.cloud.gcp.environment.GcpInstanceMetadataProvider.Companion.GCP_INSTANCE_METADATA_PATH
import misk.testing.okhttp.RoutingDispatcher
import misk.testing.okhttp.path
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class GcpInstanceMetadataProviderTest {

  @Test
  fun withInstanceName() {
    val routes = RoutingDispatcher {
      when (it.requestUrl.path) {
        "$GCP_INSTANCE_METADATA_PATH/name" ->
          MockResponse().setBody("devshell-vm-117c5946-edc9-4b32-9618-b23894057d2a")
        "$GCP_INSTANCE_METADATA_PATH/id" ->
          MockResponse().setBody("6123015407874752934")
        "$GCP_INSTANCE_METADATA_PATH/zone" ->
          MockResponse().setBody("projects/751522334863/zones/us-east1-c")
        else -> MockResponse().setResponseCode(404)
      }
    }

    val server = MockWebServer()
    server.setDispatcher(routes)
    server.start()

    val provider = GcpInstanceMetadataProvider("http://${server.hostName}:${server.port}")

    try {
      val instanceMetadata = provider.get()
      assertThat(instanceMetadata.instanceName)
          .isEqualTo("devshell-vm-117c5946-edc9-4b32-9618-b23894057d2a")
      assertThat(instanceMetadata.zone).isEqualTo("us-east1-c")
    } finally {
      server.shutdown()
    }
  }

  @Test
  fun withInstanceId() {
    val routes = RoutingDispatcher {
      when (it.requestUrl.path) {
        "$GCP_INSTANCE_METADATA_PATH/id" ->
          MockResponse().setBody("6123015407874752934")
        "$GCP_INSTANCE_METADATA_PATH/zone" ->
          MockResponse().setBody("projects/751522334863/zones/us-east1-c")
        else -> MockResponse().setResponseCode(404)
      }
    }

    val server = MockWebServer()
    server.setDispatcher(routes)
    server.start()

    val provider = GcpInstanceMetadataProvider("http://${server.hostName}:${server.port}")

    try {
      val instanceMetadata = provider.get()
      assertThat(instanceMetadata.instanceName).isEqualTo("6123015407874752934")
      assertThat(instanceMetadata.zone).isEqualTo("us-east1-c")
    } finally {
      server.shutdown()
    }
  }
}

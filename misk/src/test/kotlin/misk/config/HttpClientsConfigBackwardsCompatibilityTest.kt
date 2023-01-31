package misk.config

import misk.client.HttpClientConfig
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientEnvoyConfig
import misk.client.HttpClientsConfig
import misk.client.applyDefaults
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.config.ConfigSource
import wisp.config.WispConfig
import wisp.config.addWispConfigSources
import wisp.deployment.TESTING
import java.time.Duration

class HttpClientsConfigBackwardsCompatibilityTest {
  @Test
  fun `can parse old configuration format`() {
    val config = WispConfig.builder().addWispConfigSources(
        listOf(
          ConfigSource("classpath:/http_clients_config_old-testing.yaml"),
        )
      ).build().loadConfigOrThrow<HttpClientsConfig>()

    assertThat(config["test_client_url"])
      .isEqualTo(
        HttpClientEndpointConfig(
          url = "https://google.com/",
          clientConfig = HttpClientConfig(
            connectTimeout = Duration.ofSeconds(41),
            readTimeout = Duration.ofSeconds(42),
            writeTimeout = Duration.ofSeconds(43)
          ).applyDefaults(HttpClientsConfig.httpClientConfigDefaults)
        )
      )

    assertThat(config["test_client_envoy"])
      .isEqualTo(
        HttpClientEndpointConfig(
          envoy = HttpClientEnvoyConfig(
            app = "test_app",
            env = "test_env"
          ),
          clientConfig = HttpClientConfig(
            connectTimeout = Duration.ofSeconds(44),
            readTimeout = Duration.ofSeconds(60) // From defaults
          ).applyDefaults(HttpClientsConfig.httpClientConfigDefaults)
        )
      )
  }

  @Test
  fun `can parse new configuration format`() {
    val config = WispConfig.builder().addWispConfigSources(
      listOf(
        ConfigSource("classpath:/http_clients_config_new-testing.yaml"),
      )
    ).build().loadConfigOrThrow<HttpClientsConfig>()

    assertThat(config["test_client_url"])
      .isEqualTo(
        HttpClientEndpointConfig(
          url = "https://test.google.com/",
          clientConfig = HttpClientConfig(
            connectTimeout = Duration.ofSeconds(31),
            readTimeout = Duration.ofSeconds(32),
            writeTimeout = Duration.ofSeconds(33),
            unixSocketFile = "\u0000egress.sock",
            protocols = listOf("http1", "http2", "http3"),
            maxRequests = 199, // From defaults section
            maxRequestsPerHost = 50 // From hosts section
          ).applyDefaults(HttpClientsConfig.httpClientConfigDefaults)
        )
      )

    assertThat(config["test_client_envoy"])
      .isEqualTo(
        HttpClientEndpointConfig(
          envoy = HttpClientEnvoyConfig(
            app = "test_app",
            env = "test_env"
          ),
          clientConfig = HttpClientConfig(
            connectTimeout = Duration.ofSeconds(34),
            maxRequests = 200, // From endpoints section
            maxRequestsPerHost = 100 // From defaults section
          ).applyDefaults(HttpClientsConfig.httpClientConfigDefaults)
        )
      )
  }
}

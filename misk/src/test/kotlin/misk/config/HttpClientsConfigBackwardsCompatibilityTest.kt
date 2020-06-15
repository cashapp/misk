package misk.config

import misk.client.HttpClientConfig
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientEnvoyConfig
import misk.client.HttpClientsConfig
import misk.environment.Env
import misk.environment.Environment
import misk.resources.MemoryResourceLoaderBackend
import misk.resources.ResourceLoader
import misk.web.WebConfig
import okio.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Duration
import kotlin.test.assertFailsWith

class HttpClientsConfigBackwardsCompatibilityTest {
  @Test
  fun `can parse old configuration format`() {
    val config =
        MiskConfig.load<HttpClientsConfig>("http_clients_config_old", Env("TESTING"))

    assertThat(config["test_client_url"])
        .isEqualTo(HttpClientEndpointConfig(
            url = "https://google.com/",
            clientConfig = HttpClientConfig(
                connectTimeout = Duration.ofSeconds(41),
                readTimeout = Duration.ofSeconds(42),
                writeTimeout = Duration.ofSeconds(43)
            )
        ))

    assertThat(config["test_client_envoy"])
        .isEqualTo(HttpClientEndpointConfig(
            envoy = HttpClientEnvoyConfig(
                app = "test_app",
                env = "test_env"
            ),
            clientConfig = HttpClientConfig(
                connectTimeout = Duration.ofSeconds(44)
            )
        ))
  }

  @Test
  fun `can parse new configuration format`() {
    val config =
        MiskConfig.load<HttpClientsConfig>("http_clients_config_new", Env("TESTING"))

    assertThat(config["test_client_url"])
        .isEqualTo(HttpClientEndpointConfig(
            url = "https://google.com/",
            clientConfig = HttpClientConfig(
                connectTimeout = Duration.ofSeconds(31),
                readTimeout = Duration.ofSeconds(32),
                writeTimeout = Duration.ofSeconds(33),
                unixSocketFile = File("file.socket"),
                protocols = listOf("http1", "http2", "http3")
            )
        ))

    assertThat(config["test_client_envoy"])
        .isEqualTo(HttpClientEndpointConfig(
            envoy = HttpClientEnvoyConfig(
                app = "test_app",
                env = "test_env"
            ),
            clientConfig = HttpClientConfig(
                connectTimeout = Duration.ofSeconds(34)
            )
        ))
  }
}

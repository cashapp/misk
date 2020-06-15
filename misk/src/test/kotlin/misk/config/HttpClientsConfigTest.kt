package misk.config

import misk.client.HttpClientConfig
import misk.client.HttpClientEndpointConfig
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

class HttpClientsConfigTest {
  @Test
  fun canParseOldConfig() {
    val config =
        MiskConfig.load<HttpClientsConfig>("http_clients_config_old", Env("TESTING"))

    assertThat(config["test_client"])
        .isEqualTo(HttpClientEndpointConfig(
            url = "https://google.com/",
            clientConfig = HttpClientConfig(
                connectTimeout = Duration.ofSeconds(41),
                readTimeout = Duration.ofSeconds(42),
                writeTimeout = Duration.ofSeconds(43)
            )
        ))
  }

  @Test
  fun canParseNewConfig() {
    val config =
        MiskConfig.load<HttpClientsConfig>("http_clients_config_new", Env("TESTING"))

    assertThat(config["test_client"])
        .isEqualTo(HttpClientEndpointConfig(
            url = "https://google.com/",
            clientConfig = HttpClientConfig(
                connectTimeout = Duration.ofSeconds(31),
                readTimeout = Duration.ofSeconds(32),
                writeTimeout = Duration.ofSeconds(33)
            )
        ))
  }
}

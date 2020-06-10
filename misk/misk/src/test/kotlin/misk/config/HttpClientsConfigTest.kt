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

fun buildMemoryResourceLoader(resourceName: String, resourceContent: String) =
    ResourceLoader(
        mapOf<String, ResourceLoader.Backend>(
            "mem:" to MemoryResourceLoaderBackend().apply {
              put(resourceName, ByteString.of(*(resourceContent.toByteArray())))
            }
        ) as java.util.Map<String, ResourceLoader.Backend>
    )

class HttpClientsConfigTest {
  @Test
  fun canParseOldConfig() {
    val config =
        MiskConfig.load<HttpClientsConfig>("http_clients_config_old", Env("TESTING"))

    assertThat(config["irs"])
        .isEqualTo(HttpClientEndpointConfig(
            url = "https://google.com/",
            clientConfig = HttpClientConfig(
                connectTimeout = Duration.ofSeconds(30),
                readTimeout = Duration.ofSeconds(30),
                writeTimeout = Duration.ofSeconds(30)
            )
        ))
  }

  @Test
  fun canParseNewConfig() {
    val config =
        MiskConfig.load<HttpClientsConfig>("http_clients_config_new", Env("TESTING"))

    assertThat(config["irs"])
        .isEqualTo(HttpClientEndpointConfig(
            url = "https://google.com/",
            clientConfig = HttpClientConfig(
                connectTimeout = Duration.ofSeconds(30),
                readTimeout = Duration.ofSeconds(30),
                writeTimeout = Duration.ofSeconds(30)
            )
        ))
  }
}

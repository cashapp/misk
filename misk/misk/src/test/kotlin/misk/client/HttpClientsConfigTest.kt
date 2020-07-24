package misk.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URL
import java.time.Duration

private fun HttpClientConfig.withGlobalDefaults() = this.applyDefaults(HttpClientsConfig.httpClientConfigDefaults)

class HttpClientsConfigTest {
  @Test
  fun `matches host settings by pattern`() {
    val cfg = HttpClientsConfig(
        hostConfigs = linkedMapOf(
            ".*" to HttpClientConfig(
                connectTimeout = Duration.ofSeconds(1)
            ),
            ".*\\.com" to HttpClientConfig(
                writeTimeout = Duration.ofSeconds(2)
            ),
            ".*\\.org" to HttpClientConfig(
                readTimeout = Duration.ofSeconds(3)
            )
        )
    )

    assertThat(cfg[URL("http://test.wikipedia.org")])
        .isEqualTo(
            HttpClientEndpointConfig(
                url = "http://test.wikipedia.org",
                clientConfig = HttpClientConfig(
                    connectTimeout = Duration.ofSeconds(1),
                    readTimeout = Duration.ofSeconds(3)
                ).withGlobalDefaults()
            )
        )

    assertThat(cfg[URL("http://someting.123.domain.com")])
        .isEqualTo(
            HttpClientEndpointConfig(
                url = "http://someting.123.domain.com",
                clientConfig = HttpClientConfig(
                    connectTimeout = Duration.ofSeconds(1),
                    writeTimeout = Duration.ofSeconds(2)
                ).withGlobalDefaults()
            )
        )

    assertThat(cfg[URL("https://some.com.domain.org")])
        .isEqualTo(
            HttpClientEndpointConfig(
                url = "https://some.com.domain.org",
                clientConfig = HttpClientConfig(
                    connectTimeout = Duration.ofSeconds(1),
                    readTimeout = Duration.ofSeconds(3)
                ).withGlobalDefaults()
            )
        )
  }

  @Test
  fun `applies host settings in order of declaration`() {
    val cfg1 = HttpClientsConfig(
        hostConfigs = linkedMapOf(
            ".*" to HttpClientConfig(
                connectTimeout = Duration.ofSeconds(1)
            ),
            ".*\\.com" to HttpClientConfig(
                connectTimeout = Duration.ofSeconds(2)
            )
        )
    )

    val cfg2 = HttpClientsConfig(
        hostConfigs = linkedMapOf(
            ".*\\.com" to HttpClientConfig(
                connectTimeout = Duration.ofSeconds(2)
            ),
            ".*" to HttpClientConfig(
                connectTimeout = Duration.ofSeconds(1)
            )
        )
    )

    assertThat(cfg1[URL("http://domain.com")])
        .isEqualTo(
            HttpClientEndpointConfig(
                url = "http://domain.com",
                clientConfig = HttpClientConfig(
                    connectTimeout = Duration.ofSeconds(2)
                ).withGlobalDefaults()
            )
        )

    assertThat(cfg2[URL("http://domain.com")])
        .isEqualTo(
            HttpClientEndpointConfig(
                url = "http://domain.com",
                clientConfig = HttpClientConfig(
                    connectTimeout = Duration.ofSeconds(1)
                ).withGlobalDefaults()
            )
        )
  }

  @Test
  fun `endpoint settings are always highest priority`() {
    val cfg = HttpClientsConfig(
        hostConfigs = linkedMapOf(
            ".*" to HttpClientConfig(
                connectTimeout = Duration.ofSeconds(1)
            ),
            "domain.com" to HttpClientConfig(
                connectTimeout = Duration.ofSeconds(2)
            )
        ),
        endpoints = mapOf(
            "test" to HttpClientEndpointConfig(
                url = "http://domain.com",
                clientConfig = HttpClientConfig(
                    connectTimeout = Duration.ofSeconds(3)
                )
            )
        )
    )

    assertThat(cfg[URL("http://domain.com")])
        .isEqualTo(
            HttpClientEndpointConfig(
                url = "http://domain.com",
                clientConfig = HttpClientConfig(
                    connectTimeout = Duration.ofSeconds(2)
                ).withGlobalDefaults()
            )
        )

    assertThat(cfg["test"])
        .isEqualTo(
            HttpClientEndpointConfig(
                url = "http://domain.com",
                clientConfig = HttpClientConfig(
                    connectTimeout = Duration.ofSeconds(3)
                ).withGlobalDefaults()
            )
        )
  }
}

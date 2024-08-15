package wisp.launchdarkly

import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder.DEFAULT_CAPACITY
import wisp.client.HttpClientSSLConfig
import wisp.config.Config

data class LaunchDarklyConfig @JvmOverloads constructor(
    val sdk_key: String,
    val base_uri: String,
    val ssl: HttpClientSSLConfig? = null,
    val offline: Boolean = false,
    val event_capacity: Int = DEFAULT_CAPACITY
) : Config

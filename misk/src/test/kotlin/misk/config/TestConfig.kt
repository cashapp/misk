package misk.config

import misk.web.WebConfig

data class TestConfig(
    val app_name: String,
    val web_config: WebConfig,
    val consumer_a: ConsumerConfig,
    val consumer_b: ConsumerConfig
) : Config

data class ConsumerConfig(val max_items: Int) : Config

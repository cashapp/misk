package misk.config

import misk.web.WebConfig

data class TestConfig(
    val web: WebConfig,
    val consumer_a: ConsumerConfig,
    val consumer_b: ConsumerConfig
) : Config

data class ConsumerConfig(val min_items: Int = 0, val max_items: Int) : Config

package misk.config

import com.google.common.truth.Truth.assertThat
import misk.testing.MiskTestRule
import misk.web.WebConfig
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import javax.inject.Named

class ConfigTest {
    @get:Rule
    val miskTestRule = MiskTestRule(
            ConfigModule.create<TestConfig>("test-config.yaml")
    )

    @Inject lateinit var test_config: TestConfig

    @field:[Inject Named("consumer_a")] lateinit var consumer_a_config: ConsumerConfig

    @field:[Inject Named("consumer_b")] lateinit var consumer_b_config: ConsumerConfig

    @Test
    fun testConfigIsProperlyParsed() {
        assertThat(test_config.web_config).isEqualTo(WebConfig(1234, 30_000))
        assertThat(test_config.app_name).isEqualTo("test-app")
        assertThat(test_config.consumer_a).isEqualTo(ConsumerConfig(1))
        assertThat(test_config.consumer_b).isEqualTo(ConsumerConfig(2))
    }

    @Test
    fun subConfigsAreNamedProperly() {
        assertThat(consumer_a_config).isEqualTo(ConsumerConfig(1))
        assertThat(consumer_b_config).isEqualTo(ConsumerConfig(2))
    }
}

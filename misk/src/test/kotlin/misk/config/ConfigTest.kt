package misk.config

import com.google.inject.Guice
import com.google.inject.ProvisionException
import com.google.inject.util.Modules
import misk.environment.Environment.TESTING
import misk.environment.EnvironmentModule
import misk.inject.getInstance
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Named

@MiskTest
class ConfigTest {
  @MiskTestModule
  val module = Modules.combine(
      ConfigModule.create<TestConfig>("test_app"),
      EnvironmentModule(TESTING)
  )

  @Inject
  private lateinit var testConfig: TestConfig

  @field:[Inject Named("consumer_a")]
  lateinit var consumerA: ConsumerConfig

  @field:[Inject Named("consumer_b")]
  lateinit var consumerB: ConsumerConfig

  @Test
  fun testConfigIsProperlyParsed() {
    assertThat(testConfig.web).isEqualTo(WebConfig(5678, 30_000))
    assertThat(testConfig.consumer_a).isEqualTo(ConsumerConfig(0, 1))
    assertThat(testConfig.consumer_b).isEqualTo(ConsumerConfig(1, 2))
  }

  @Test
  fun subConfigsAreNamedProperly() {
    assertThat(consumerA).isEqualTo(ConsumerConfig(0, 1))
    assertThat(consumerB).isEqualTo(ConsumerConfig(1, 2))
  }

  @Test
  fun environmentConfigOverridesCommon() {
    assertThat(testConfig.web.port).isEqualTo(5678)
  }

  @Test
  fun defaultValuesAreUsed() {
    assertThat(testConfig.consumer_a.min_items).isEqualTo(0)
  }

  @Test
  fun friendlyErrorMessagesWhenFilesNotFound() {
    val module = Modules.combine(
        ConfigModule.create<TestConfig>("missing"),
        EnvironmentModule(TESTING)
    )

    val exception = assertThrows(ProvisionException::class.java) {
      Guice.createInjector(module).getInstance<TestConfig>()
    }

    assertThat(exception.errorMessages.map { it.message }).anySatisfy {
      assertThat(it).contains(
          "could not find configuration files - checked [missing-common.yaml, missing-testing.yaml]"
      )
    }
  }

  @Test
  fun friendlyErrorMessageWhenConfigPropertyMissing() {
    val module = Modules.combine(
        ConfigModule.create<TestConfig>("partial_test_app"),
        EnvironmentModule(TESTING)
    )

    val exception  = assertThrows(ProvisionException::class.java) {
      Guice.createInjector(module).getInstance<TestConfig>()
    }

    assertThat(exception.errorMessages.map { it.message }).anySatisfy {
      assertThat(it).contains("could not find configuration for consumer_a")
    }
  }

  @Test
  fun friendlyErrorMessagesWhenFileUnparseable() {
    val module = Modules.combine(
        ConfigModule.create<TestConfig>("unparsable"),
        EnvironmentModule(TESTING)
    )

    val exception = assertThrows(ProvisionException::class.java) {
      Guice.createInjector(module).getInstance<TestConfig>()
    }

    assertThat(exception.errorMessages.map { it.message }).anySatisfy {
      assertThat(it).contains("could not parse unparsable-common.yaml")
    }
  }

}

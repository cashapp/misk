package misk.web.metadata.config

import com.google.inject.Provider
import com.google.inject.util.Modules
import jakarta.inject.Inject
import kotlin.test.assertEquals
import misk.config.MiskConfig
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.metadata.MetadataTestingModule
import misk.web.metadata.TestConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING

@MiskTest(startService = true)
class ConfigMetadataTest {
  @MiskTestModule val module = MetadataTestingModule()

  @Inject internal lateinit var configMetadataProvider: Provider<ConfigMetadata>

  @Test
  fun configSecretsStillAccessibleInCode() {
    val config = MiskConfig.load<TestConfig>("admin-dashboard-app", TESTING)

    assertEquals("testing", config.included.key)
    assertEquals("abc123", config.password.password)
    assertEquals("abc123", config.password.passphrase)
    assertEquals("abc123", config.password.custom)
    assertEquals("abc123", config.secret.secret_key.value)
    assertEquals("abc123", config.redacted.key)
  }

  @Test
  fun passesAlongEffectiveConfigWithRedaction() {
    val response = configMetadataProvider.get()
    assertThat(response.resources).containsKey("Effective Config")

    val effectiveConfig = response.resources.get("Effective Config")

    assertEquals(
      """
      |---
      |included:
      |  key: "foo"
      |overridden:
      |  key: "bar"
      |password:
      |  password: "████████"
      |  passphrase: "████████"
      |  custom: "████████"
      |secret:
      |  secret_key: "reference -> ████████"
      |redacted: "████████"
      |"""
        .trimMargin(),
      effectiveConfig,
    )
  }

  @Test
  fun passesAlongFullUnderlyingConfigResources() {
    val response = configMetadataProvider.get()
    assertThat(response.resources).containsKey("classpath:/admin-dashboard-app-common.yaml")
    assertThat(response.resources).containsKey("classpath:/admin-dashboard-app-testing.yaml")

    val commonConfig = response.resources.get("classpath:/admin-dashboard-app-common.yaml")
    val testingConfig = response.resources.get("classpath:/admin-dashboard-app-testing.yaml")

    // ignored included because full file is passed along
    assertThat(commonConfig).contains("common", "ignored")
    assertThat(testingConfig).contains("testing")
  }

  @Test
  fun doesNotRedactRawConfigFiles() {
    val response = configMetadataProvider.get()
    assertThat(response.resources).containsKey("classpath:/admin-dashboard-app-common.yaml")
    assertThat(response.resources).containsKey("Effective Config")

    val commonConfig = response.resources.get("classpath:/admin-dashboard-app-common.yaml")
    val effectiveConfig = response.resources.get("Effective Config")

    assertThat(commonConfig).contains("common123")
    assertThat(effectiveConfig).doesNotContain("pass1", "phrase2")
  }
}

class ConfigTabModeModule(private val mode: ConfigMetadataAction.ConfigTabMode) : KAbstractModule() {
  override fun configure() {
    bind<ConfigMetadataAction.ConfigTabMode>().toInstance(mode)
  }
}

@MiskTest(startService = true)
class ConfigMetadataActionSafeTest {
  @MiskTestModule
  val module =
    Modules.override(MetadataTestingModule()).with(ConfigTabModeModule(ConfigMetadataAction.ConfigTabMode.SAFE))

  @Inject internal lateinit var configMetadataProvider: Provider<ConfigMetadata>

  @Test
  fun secureModeDoesNotIncludeEffectiveConfigOrRawYamlFiles() {
    val response = configMetadataProvider.get()
    assertThat(response.resources).doesNotContainKey("Effective Config")
    assertThat(response.resources).doesNotContainKey("classpath:/admin-dashboard-app-common.yaml")
    assertThat(response.resources).doesNotContainKey("classpath:/admin-dashboard-app-testing.yaml")
  }
}

@MiskTest(startService = true)
class ConfigMetadataActionRedactedTest {
  @MiskTestModule
  val module =
    Modules.override(MetadataTestingModule())
      .with(ConfigTabModeModule(ConfigMetadataAction.ConfigTabMode.SHOW_REDACTED_EFFECTIVE_CONFIG))

  @Inject internal lateinit var configMetadataProvider: Provider<ConfigMetadata>

  @Test
  fun showEffectiveConfigModeDoesNotIncludeRawYamlFiles() {
    val response = configMetadataProvider.get()
    assertThat(response.resources).containsKey("Effective Config")
    assertThat(response.resources).doesNotContainKey("classpath:/admin-dashboard-app-common.yaml")
    assertThat(response.resources).doesNotContainKey("classpath:/admin-dashboard-app-testing.yaml")
  }
}

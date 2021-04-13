package misk.web.metadata.config

import misk.environment.Environment
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.metadata.MetadataTestingModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wisp.config.Config

@MiskTest(startService = true)
class ConfigMetadataActionTest {
  @MiskTestModule
  val module = MetadataTestingModule()

  val testConfig = TestConfig(
    IncludedConfig("foo"),
    OverriddenConfig("bar"),
    RedactedConfig("pass1", "phrase2")
  )

  lateinit var configMetadataAction: ConfigMetadataAction

  @BeforeEach fun beforeEach() {
    configMetadataAction = ConfigMetadataAction(
      "admin_dashboard_app",
      Environment.TESTING,
      testConfig
    )
  }

  @Test fun passesAlongEffectiveConfig() {
    val response = configMetadataAction.getAll()
    assertThat(response.resources).containsKey("Effective Config")

    val effectiveConfig = response.resources.get("Effective Config")

    assertThat(effectiveConfig).contains("foo", "bar")
  }

  @Test fun passesAlongFullUnderlyingConfigResources() {
    val response = configMetadataAction.getAll()
    assertThat(response.resources).containsKey("classpath:/admin_dashboard_app-common.yaml")
    assertThat(response.resources).containsKey("classpath:/admin_dashboard_app-testing.yaml")

    val commonConfig = response.resources.get("classpath:/admin_dashboard_app-common.yaml")
    val testingConfig = response.resources.get("classpath:/admin_dashboard_app-testing.yaml")

    // ignored included because full file is passed along
    assertThat(commonConfig).contains("common", "ignored")
    assertThat(testingConfig).contains("testing")
  }

  @Test fun redactsConfig() {
    val response = configMetadataAction.getAll()
    assertThat(response.resources).containsKey("classpath:/admin_dashboard_app-common.yaml")
    assertThat(response.resources).containsKey("Effective Config")

    val commonConfig = response.resources.get("classpath:/admin_dashboard_app-common.yaml")
    val effectiveConfig = response.resources.get("Effective Config")

    assertThat(commonConfig).doesNotContain("phrase123")
    assertThat(effectiveConfig).doesNotContain("pass1", "phrase2")
  }

  data class TestConfig(
    val includedConfig: IncludedConfig,
    val overriddenConfig: OverriddenConfig,
    val redactedConfig: RedactedConfig
  ) : Config

  data class IncludedConfig(val key: String) : Config
  data class OverriddenConfig(val key: String) : Config
  data class RedactedConfig(val password: String, val passphrase: String) : Config
}

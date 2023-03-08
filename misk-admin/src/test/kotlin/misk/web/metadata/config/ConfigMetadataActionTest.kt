package misk.web.metadata.config

import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.metadata.MetadataTestingModule
import misk.web.metadata.jvm.JvmMetadataAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wisp.config.Config
import wisp.deployment.TESTING
import javax.inject.Inject

@MiskTest(startService = true)
class ConfigMetadataActionTest {
  @MiskTestModule
  val module = MetadataTestingModule()

  val testConfig = TestConfig(
    IncludedConfig("foo"),
    OverriddenConfig("bar"),
    RedactedConfig("pass1", "phrase2")
  )

  @Inject lateinit var jvmMetadataAction: JvmMetadataAction
  lateinit var configMetadataAction: ConfigMetadataAction

  @BeforeEach fun beforeEach() {
    configMetadataAction = ConfigMetadataAction(
      appName = "admin_dashboard_app",
      deployment = TESTING,
      config = testConfig,
      jvmMetadataAction = jvmMetadataAction,
      mode = ConfigMetadataAction.ConfigTabMode.UNSAFE_LEAK_MISK_SECRETS
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

  @Test fun passesAlongJvmConfig() {
    val response = configMetadataAction.getAll()
    assertThat(response.resources).containsKey("Effective Config")
    assertThat(response.resources).containsKey("JVM")
    val jvmConfig = jvmMetadataAction.getRuntime()
    assertThat(response.resources.get("JVM")).contains("Java Virtual Machine Specification")
  }

  @Test fun secureModeDoesNotIncludeYamlFiles() {
    configMetadataAction = ConfigMetadataAction(
      appName = "admin_dashboard_app",
      deployment = TESTING,
      config = testConfig,
      jvmMetadataAction = jvmMetadataAction,
      mode = ConfigMetadataAction.ConfigTabMode.SAFE
    )

    val response = configMetadataAction.getAll()
    assertThat(response.resources).doesNotContainKey("classpath:/admin_dashboard_app-common.yaml")
    assertThat(response.resources).doesNotContainKey("classpath:/admin_dashboard_app-testing.yaml")
    assertThat(response.resources).containsKey("JVM")
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

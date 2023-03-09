package misk.web.metadata.config

import misk.config.MiskConfig
import misk.config.Secret
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
import kotlin.test.assertEquals

@MiskTest(startService = true)
class ConfigMetadataActionTest {
  @MiskTestModule
  val module = MetadataTestingModule()

  val testConfig = TestConfig(
    IncludedConfig("foo"),
    OverriddenConfig("bar"),
    RedactedConfig("pass1", "phrase2", "custom3"),
    SecretConfig(MiskConfig.RealSecret("value"))
  )

  @Inject lateinit var jvmMetadataAction: JvmMetadataAction
  lateinit var configMetadataAction: ConfigMetadataAction

  @BeforeEach fun beforeEach() {
    configMetadataAction = ConfigMetadataAction(
      appName = "admin_dashboard_app",
      deployment = TESTING,
      config = testConfig,
      jvmMetadataAction = jvmMetadataAction,
      mode = ConfigMetadataAction.ConfigTabMode.UNSAFE_LEAK_MISK_SECRETS,
      keysToRedact = listOf("password", "passphrase")
    )
  }

  @Test fun passesAlongEffectiveConfigWithRedaction() {
    val response = configMetadataAction.getAll()
    assertThat(response.resources).containsKey("Effective Config")

    val effectiveConfig = response.resources.get("Effective Config")

    assertEquals("""
      |---
      |includedConfig:
      |  key: "foo"
      |overriddenConfig:
      |  key: "bar"
      |redactedConfig:
      |  password: ████████
      |  passphrase: ████████
      |  custom: "custom3"
      |secretConfig:
      |  secretKey: "████████"
      |
    """.trimMargin(), effectiveConfig)
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
    val jvmRuntime = jvmMetadataAction.getRuntime()
    assertThat(response.resources.get("JVM")).contains("Java Virtual Machine Specification")
    assertThat(response.resources.get("JVM")).contains("pid")
    assertThat(response.resources.get("JVM")).contains("\"vm_name\": \"OpenJDK 64-Bit Server VM\",")
    assertThat(response.resources.get("JVM")).contains("\"vm_vendor\": \"AdoptOpenJDK\",")
    assertThat(response.resources.get("JVM")).contains("class_path")
  }

  @Test fun secureModeDoesNotIncludeYamlFiles() {
    configMetadataAction = ConfigMetadataAction(
      appName = "admin_dashboard_app",
      deployment = TESTING,
      config = testConfig,
      jvmMetadataAction = jvmMetadataAction,
      mode = ConfigMetadataAction.ConfigTabMode.SAFE,
      keysToRedact = listOf("password", "passphrase")
    )

    val response = configMetadataAction.getAll()
    assertThat(response.resources).doesNotContainKey("classpath:/admin_dashboard_app-common.yaml")
    assertThat(response.resources).doesNotContainKey("classpath:/admin_dashboard_app-testing.yaml")
    assertThat(response.resources).containsKey("JVM")
  }

  @Test fun customKeyToRedact() {
    configMetadataAction = ConfigMetadataAction(
      appName = "admin_dashboard_app",
      deployment = TESTING,
      config = testConfig,
      jvmMetadataAction = jvmMetadataAction,
      mode = ConfigMetadataAction.ConfigTabMode.SAFE,
      keysToRedact = listOf("custom", "password", "passphrase")
    )

    val response = configMetadataAction.getAll()

    val effectiveConfig = response.resources.get("Effective Config")

    assertEquals("""
      |---
      |includedConfig:
      |  key: "foo"
      |overriddenConfig:
      |  key: "bar"
      |redactedConfig:
      |  password: ████████
      |  passphrase: ████████
      |  custom: ████████
      |secretConfig:
      |  secretKey: "████████"
      |
    """.trimMargin(), effectiveConfig)
  }

  data class TestConfig(
    val includedConfig: IncludedConfig,
    val overriddenConfig: OverriddenConfig,
    val redactedConfig: RedactedConfig,
    val secretConfig: SecretConfig
  ) : Config

  data class IncludedConfig(val key: String) : Config
  data class OverriddenConfig(val key: String) : Config
  data class RedactedConfig(val password: String, val passphrase: String, val custom: String) : Config
  data class SecretConfig(val secretKey: Secret<String>) : Config
}

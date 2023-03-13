package misk.web.metadata.config

import misk.config.MiskConfig
import misk.config.RedactInDashboard
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
    PasswordConfig("pass1", "phrase2", "custom3"),
    SecretConfig(MiskConfig.RealSecret("value")),
    RedactedConfig("baz")
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

  @Test fun passesAlongEffectiveConfigWithRedaction() {
    val response = configMetadataAction.getAll()
    assertThat(response.resources).containsKey("Effective Config")

    val effectiveConfig = response.resources.get("Effective Config")

    assertEquals(
      """
      |---
      |includedConfig:
      |  key: "foo"
      |overriddenConfig:
      |  key: "bar"
      |passwordConfig:
      |  password: "████████"
      |  passphrase: "████████"
      |  custom: "████████"
      |secretConfig:
      |  secretKey: "████████"
      |redactedConfig: "████████"
      |
    """.trimMargin(), effectiveConfig
    )
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

  @Test fun doesNotRedactRawConfigFiles() {
    val response = configMetadataAction.getAll()
    assertThat(response.resources).containsKey("classpath:/admin_dashboard_app-common.yaml")
    assertThat(response.resources).containsKey("Effective Config")

    val commonConfig = response.resources.get("classpath:/admin_dashboard_app-common.yaml")
    val effectiveConfig = response.resources.get("Effective Config")

    assertThat(commonConfig).contains("phrase123")
    assertThat(effectiveConfig).contains("pass1", "phrase2")
  }

  @Test fun passesAlongJvmConfig() {
    val response = configMetadataAction.getAll()
    assertThat(response.resources).containsKey("Effective Config")
    assertThat(response.resources).containsKey("JVM")
    val configJvm = response.resources.get("JVM")
    val jvmRuntime = jvmMetadataAction.getRuntime()
    assertEquals(
      // uptime millis will differ given the different calls from config and jvm action
      configJvm?.lines()?.filter { !it.contains("uptime_millis")}?.joinToString(),
      jvmRuntime.lines().filter { !it.contains("uptime_millis") }.joinToString()
    )
    assertThat(configJvm).contains("Java Virtual Machine Specification")
    assertThat(configJvm).contains("pid")
    assertThat(configJvm).contains("\"vm_name\": \"OpenJDK 64-Bit Server VM\",")
    assertThat(configJvm).contains("\"vm_vendor\": \"AdoptOpenJDK\",")
    assertThat(configJvm).contains("class_path")
  }

  @Test fun secureModeDoesNotIncludeRawYamlFiles() {
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
    val passwordConfig: PasswordConfig,
    val secretConfig: SecretConfig,
    val redactedConfig: RedactedConfig
  ) : Config

  data class IncludedConfig(val key: String) : Config
  data class OverriddenConfig(val key: String) : Config

  data class PasswordConfig(
    @RedactInDashboard
    val password: String,
    @RedactInDashboard
    val passphrase: String,
    @RedactInDashboard
    val custom: String
  ) : Config

  @RedactInDashboard
  data class RedactedConfig(
    val key: String
  )

  data class SecretConfig(val secretKey: Secret<String>) : Config
}

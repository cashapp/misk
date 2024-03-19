package misk.web.metadata.config

import jakarta.inject.Inject
import misk.config.MiskConfig
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.metadata.MetadataTestingModule
import misk.web.metadata.TestConfig
import misk.web.metadata.jvm.JvmMetadataAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING
import kotlin.test.assertEquals

@MiskTest(startService = true)
class ConfigMetadataActionTest {
  @MiskTestModule
  val module = MetadataTestingModule()

//  val testConfig = TestConfig(
//    IncludedConfig("foo"),
//    OverriddenConfig("bar"),
//    PasswordConfig("pass1", "phrase2", "custom3"),
//    SecretConfig(MiskConfig.RealSecret("value", "reference")),
//    RedactedConfig("baz")
//  )

  @Inject lateinit var jvmMetadataAction: JvmMetadataAction
  @Inject lateinit var configMetadataAction: ConfigMetadataAction

  @Test fun configSecretsStillAccessibleInCode() {
    val config = MiskConfig.load<TestConfig>("admin-dashboard-app", TESTING)

    assertEquals("testing", config.included.key)
    assertEquals("abc123", config.password.password)
    assertEquals("abc123", config.password.passphrase)
    assertEquals("abc123", config.password.custom)
    assertEquals("abc123", config.secret.secret_key.value)
    assertEquals("abc123", config.redacted.key)
  }

  @Test fun passesAlongEffectiveConfigWithRedaction() {
    val response = configMetadataAction.getAll()
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
      |
    """.trimMargin(), effectiveConfig
    )
  }

  @Test fun passesAlongFullUnderlyingConfigResources() {
    val response = configMetadataAction.getAll()
    assertThat(response.resources).containsKey("classpath:/admin-dashboard-app-common.yaml")
    assertThat(response.resources).containsKey("classpath:/admin-dashboard-app-testing.yaml")

    val commonConfig = response.resources.get("classpath:/admin-dashboard-app-common.yaml")
    val testingConfig = response.resources.get("classpath:/admin-dashboard-app-testing.yaml")

    // ignored included because full file is passed along
    assertThat(commonConfig).contains("common", "ignored")
    assertThat(testingConfig).contains("testing")
  }

  @Test fun doesNotRedactRawConfigFiles() {
    val response = configMetadataAction.getAll()
    assertThat(response.resources).containsKey("classpath:/admin-dashboard-app-common.yaml")
    assertThat(response.resources).containsKey("Effective Config")

    val commonConfig = response.resources.get("classpath:/admin-dashboard-app-common.yaml")
    val effectiveConfig = response.resources.get("Effective Config")

    assertThat(commonConfig).contains("common123")
    assertThat(effectiveConfig).doesNotContain("pass1", "phrase2")
  }

  @Test fun passesAlongJvmConfig() {
    val response = configMetadataAction.getAll()
    assertThat(response.resources).containsKey("Effective Config")
    assertThat(response.resources).containsKey("JVM")
    val configJvm = response.resources.get("JVM")
    val jvmRuntime = jvmMetadataAction.getRuntime()
    assertEquals(
      // uptime millis will differ given the different calls from config and jvm action
      configJvm?.lines()?.filter { !it.contains("uptime_millis") }?.joinToString(),
      jvmRuntime.lines().filter { !it.contains("uptime_millis") }.joinToString()
    )
    assertThat(configJvm).contains("Java Virtual Machine Specification")
    assertThat(configJvm).contains("pid")
    assertThat(configJvm).contains("vm_name")
    assertThat(configJvm).contains("vm_vendor")
    assertThat(configJvm).contains("class_path")
  }

//  @Test fun secureModeDoesNotIncludeEffectiveConfigOrRawYamlFiles() {
//    configMetadataAction = ConfigMetadataAction(
//      appName = "admin-dashboard-app",
//      deployment = TESTING,
//      config = testConfig,
//      jvmMetadataAction = jvmMetadataAction,
//      mode = ConfigMetadataAction.ConfigTabMode.SAFE
//    )
//
//    val response = configMetadataAction.getAll()
//    assertThat(response.resources).doesNotContainKey("Effective Config")
//    assertThat(response.resources).doesNotContainKey("classpath:/admin-dashboard-app-common.yaml")
//    assertThat(response.resources).doesNotContainKey("classpath:/admin-dashboard-app-testing.yaml")
//    assertThat(response.resources).containsKey("JVM")
//  }
//
//  @Test fun showEffectiveConfigModeDoesNotIncludeRawYamlFiles() {
//    configMetadataAction = ConfigMetadataAction(
//      appName = "admin-dashboard-app",
//      deployment = TESTING,
//      config = testConfig,
//      jvmMetadataAction = jvmMetadataAction,
//      mode = ConfigMetadataAction.ConfigTabMode.SHOW_REDACTED_EFFECTIVE_CONFIG
//    )
//
//    val response = configMetadataAction.getAll()
//    assertThat(response.resources).containsKey("Effective Config")
//    assertThat(response.resources).doesNotContainKey("classpath:/admin-dashboard-app-common.yaml")
//    assertThat(response.resources).doesNotContainKey("classpath:/admin-dashboard-app-testing.yaml")
//    assertThat(response.resources).containsKey("JVM")
//  }
//

}

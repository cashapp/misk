package misk.feature.testing

import com.google.inject.util.Modules
import misk.feature.AppScopedDynamicConfigModule
import misk.feature.AppScopedDynamicConfigResolver
import misk.feature.Feature
import misk.feature.ValidatableConfig
import misk.logging.LogCollector
import misk.logging.LogCollectorService
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class AppScopedDynamicConfigModuleTest {
  private val safeDefaultThing = Thing(listOf(), someKnob = false, someThreshold = 0L)
  private val safeDefaultStuff = Stuff(usefulEmergencyLever = false, boomSwitch = false)

  @MiskTestModule
  private val testModule = Modules.combine(TestModule(),
    AppScopedDynamicConfigModule.create("thing-config", Thing::class, safeDefaultThing),
    AppScopedDynamicConfigModule.create("stuff-config", Stuff::class, safeDefaultStuff))

  @Inject private lateinit var thingConfigResolver: AppScopedDynamicConfigResolver<Thing>
  @Inject private lateinit var stuffConfigResolver: AppScopedDynamicConfigResolver<Stuff>
  @Inject private lateinit var fakeFeatureFlags: FakeFeatureFlags
  @Inject private lateinit var logCollector: LogCollector
  @Inject private lateinit var logCollectorService: LogCollectorService

  @BeforeEach
  fun setup() {
    fakeFeatureFlags.reset()
    logCollectorService.startAsync()
    logCollectorService.awaitRunning()
    logCollector.takeMessages()
  }

  @AfterEach
  fun teardown() {
    logCollectorService.stopAsync()
    logCollectorService.awaitTerminated()
  }

  @Test
  fun `no feature registered, returns failure mode default and warns`() {
    val thingConfig = thingConfigResolver.resolveConfig()

    assertThat(thingConfig).isEqualTo(safeDefaultThing)
    assertThat(logCollector.takeMessages(AppScopedDynamicConfigResolver::class)).anyMatch {
      it.contains("Failed to retrieve or parse JSON for config override 'test-app-thing-config'")
    }

    val stuffConfig = stuffConfigResolver.resolveConfig()
    assertThat(stuffConfig).isEqualTo(safeDefaultStuff)
    assertThat(logCollector.takeMessages(AppScopedDynamicConfigResolver::class)).anyMatch {
      it.contains("Failed to retrieve or parse JSON for config override 'test-app-stuff-config'")
    }
  }

  @Test
  fun `feature registered, returns feature value`() {
    // Registering the expected feature name with a valid JSON blob yields the override
    val overrideThing = Thing(someKnob = true, someThreshold = 50L, complicatedProperties = listOf(
      ComplicatedProperty("foo", 1337L, 42)))
    fakeFeatureFlags.override(Feature("test-app-thing-config"), overrideThing, Thing::class.java)

    assertThat(thingConfigResolver.resolveConfig()).isEqualTo(overrideThing)
    assertThat(logCollector.takeMessages(AppScopedDynamicConfigResolver::class)).isEmpty()

    // Try again with a "Stuff"
    val overrideStuff = Stuff(usefulEmergencyLever = true, boomSwitch = false)
    fakeFeatureFlags.override(Feature("test-app-stuff-config"), overrideStuff, Stuff::class.java)

    assertThat(stuffConfigResolver.resolveConfig()).isEqualTo(overrideStuff)
    assertThat(logCollector.takeMessages(AppScopedDynamicConfigResolver::class)).isEmpty()
  }

  @Test
  fun `feature lookup is scoped to calling app name`() {
    val otherServiceOverride =
        Thing(someKnob = false, someThreshold = 123L, complicatedProperties = listOf(
          ComplicatedProperty("foo", 1234L, 0)))
    fakeFeatureFlags.override(Feature("other-app-thing-config"), otherServiceOverride,
      Thing::class.java)

    assertThat(thingConfigResolver.resolveConfig()).isEqualTo(safeDefaultThing)
  }

  @Test
  fun `feature set to value that cannot be parsed, warns and uses failure mode value`() {
    fakeFeatureFlags.override(Feature("test-app-thing-config"), Wrong("yikes"), Wrong::class.java)

    assertThat(thingConfigResolver.resolveConfig())
        .isEqualTo(safeDefaultThing)

    assertThat(logCollector.takeMessages(AppScopedDynamicConfigResolver::class)).anyMatch {
      it.contains("Failed to retrieve or parse JSON for config override 'test-app-thing-config'")
    }
  }

  @Test
  fun `feature set to value that can be parsed but fails validation, warns and uses failure mode value`() {
    fakeFeatureFlags.override(Feature("test-app-stuff-config"),
      Stuff(usefulEmergencyLever = true, boomSwitch = true), Stuff::class.java)

    assertThat(stuffConfigResolver.resolveConfig())
        .isEqualTo(safeDefaultStuff)

    assertThat(logCollector.takeMessages(AppScopedDynamicConfigResolver::class)).anyMatch {
      it.contains("Failed to retrieve or parse JSON for config override 'test-app-stuff-config'")
    }
  }

  private data class ComplicatedProperty(val foo: String, val bar: Long, val reality: Byte)
  private data class Stuff(val usefulEmergencyLever: Boolean, val boomSwitch: Boolean) :
      ValidatableConfig<Stuff> {
    override fun validate() {
      require(!boomSwitch)
    }
  }

  private data class Thing(
    val complicatedProperties: Collection<ComplicatedProperty>,
    val someKnob: Boolean,
    val someThreshold: Long
  ) : ValidatableConfig<Thing> {
    override fun validate() {
      require(someThreshold > 0) {
        "Negative threshold doesn't make sense for a Thing"
      }
    }
  }

  private data class Wrong(val tooBad: String)
}
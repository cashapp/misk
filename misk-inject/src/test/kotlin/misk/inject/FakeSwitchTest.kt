package misk.inject

import com.google.inject.Guice
import jakarta.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest
class FakeSwitchTest {
  @MiskTestModule
  val module =
    object : KAbstractModule() {
      override fun configure() {
        bind<Switch>().to<FakeSwitch>()
      }
    }

  @Inject lateinit var switch: Switch

  private val fakeSwitch
    get() = switch as FakeSwitch

  @Test
  fun `starts with all keys disabled`() {
    assertThat(fakeSwitch.isEnabled("any-key")).isFalse()
    assertThat(fakeSwitch.isDisabled("any-key")).isTrue()
    assertThat(fakeSwitch.getEnabledKeys()).isEmpty()
  }

  @Test
  fun `enable single key`() {
    fakeSwitch.enable("feature-a")

    assertThat(fakeSwitch.isEnabled("feature-a")).isTrue()
    assertThat(fakeSwitch.isDisabled("feature-a")).isFalse()
    assertThat(fakeSwitch.isEnabled("feature-b")).isFalse()
    assertThat(fakeSwitch.getEnabledKeys()).containsExactly("feature-a")
  }

  @Test
  fun `disable single key`() {
    fakeSwitch.enable("feature-a")
    fakeSwitch.disable("feature-a")

    assertThat(fakeSwitch.isEnabled("feature-a")).isFalse()
    assertThat(fakeSwitch.getEnabledKeys()).isEmpty()
  }

  @Test
  fun `enable multiple keys`() {
    fakeSwitch.enable("feature-a")
    fakeSwitch.enable("feature-b")
    fakeSwitch.enable("feature-c")

    assertThat(fakeSwitch.isEnabled("feature-a")).isTrue()
    assertThat(fakeSwitch.isEnabled("feature-b")).isTrue()
    assertThat(fakeSwitch.isEnabled("feature-c")).isTrue()
    assertThat(fakeSwitch.getEnabledKeys()).containsExactlyInAnyOrder("feature-a", "feature-b", "feature-c")
  }

  @Test
  fun `enableAll with multiple keys`() {
    fakeSwitch.enableAll("feature-a", "feature-b", "feature-c")

    assertThat(fakeSwitch.isEnabled("feature-a")).isTrue()
    assertThat(fakeSwitch.isEnabled("feature-b")).isTrue()
    assertThat(fakeSwitch.isEnabled("feature-c")).isTrue()
    assertThat(fakeSwitch.getEnabledKeys()).containsExactlyInAnyOrder("feature-a", "feature-b", "feature-c")
  }

  @Test
  fun `disableAll with multiple keys`() {
    fakeSwitch.enableAll("feature-a", "feature-b", "feature-c")
    fakeSwitch.disableAll("feature-a", "feature-c")

    assertThat(fakeSwitch.isEnabled("feature-a")).isFalse()
    assertThat(fakeSwitch.isEnabled("feature-b")).isTrue()
    assertThat(fakeSwitch.isEnabled("feature-c")).isFalse()
    assertThat(fakeSwitch.getEnabledKeys()).containsExactly("feature-b")
  }

  @Test
  fun `toggle key from disabled to enabled`() {
    fakeSwitch.toggle("feature-a")

    assertThat(fakeSwitch.isEnabled("feature-a")).isTrue()
  }

  @Test
  fun `toggle key from enabled to disabled`() {
    fakeSwitch.enable("feature-a")
    fakeSwitch.toggle("feature-a")

    assertThat(fakeSwitch.isEnabled("feature-a")).isFalse()
  }

  @Test
  fun `toggle key multiple times`() {
    fakeSwitch.toggle("feature-a")
    assertThat(fakeSwitch.isEnabled("feature-a")).isTrue()

    fakeSwitch.toggle("feature-a")
    assertThat(fakeSwitch.isEnabled("feature-a")).isFalse()

    fakeSwitch.toggle("feature-a")
    assertThat(fakeSwitch.isEnabled("feature-a")).isTrue()
  }

  @Test
  fun `clear removes all enabled keys`() {
    fakeSwitch.enableAll("feature-a", "feature-b", "feature-c")
    fakeSwitch.clear()

    assertThat(fakeSwitch.isEnabled("feature-a")).isFalse()
    assertThat(fakeSwitch.isEnabled("feature-b")).isFalse()
    assertThat(fakeSwitch.isEnabled("feature-c")).isFalse()
    assertThat(fakeSwitch.getEnabledKeys()).isEmpty()
  }

  @Test
  fun `enabling same key multiple times is idempotent`() {
    fakeSwitch.enable("feature-a")
    fakeSwitch.enable("feature-a")
    fakeSwitch.enable("feature-a")

    assertThat(fakeSwitch.getEnabledKeys()).containsExactly("feature-a")
  }

  @Test
  fun `disabling already disabled key is safe`() {
    fakeSwitch.disable("feature-a")

    assertThat(fakeSwitch.isEnabled("feature-a")).isFalse()
    assertThat(fakeSwitch.getEnabledKeys()).isEmpty()
  }

  @Test
  fun `ifEnabled executes block when key is enabled`() {
    fakeSwitch.enable("feature-a")
    var executed = false

    fakeSwitch.ifEnabled("feature-a") { executed = true }

    assertThat(executed).isTrue()
  }

  @Test
  fun `ifEnabled does not execute block when key is disabled`() {
    var executed = false

    fakeSwitch.ifEnabled("feature-a") { executed = true }

    assertThat(executed).isFalse()
  }

  @Test
  fun `ifDisabled executes block when key is disabled`() {
    var executed = false

    fakeSwitch.ifDisabled("feature-a") { executed = true }

    assertThat(executed).isTrue()
  }

  @Test
  fun `ifDisabled does not execute block when key is enabled`() {
    fakeSwitch.enable("feature-a")
    var executed = false

    fakeSwitch.ifDisabled("feature-a") { executed = true }

    assertThat(executed).isFalse()
  }

  @Test
  fun `reset clears all enabled keys`() {
    fakeSwitch.enableAll("feature-a", "feature-b", "feature-c")
    fakeSwitch.reset()

    assertThat(fakeSwitch.isEnabled("feature-a")).isFalse()
    assertThat(fakeSwitch.isEnabled("feature-b")).isFalse()
    assertThat(fakeSwitch.isEnabled("feature-c")).isFalse()
    assertThat(fakeSwitch.getEnabledKeys()).isEmpty()
  }

  @Test
  fun `reset is called between tests when using MiskTest`() {
    // This test verifies that the fixture is properly reset between test runs
    // State from previous tests should not affect this test
    assertThat(fakeSwitch.getEnabledKeys()).isEmpty()
  }

  @Test
  fun `can be used as AsyncSwitch`() {
    val module =
      object : KAbstractModule() {
        override fun configure() {
          bind<AsyncSwitch>().to<FakeSwitch>()
        }
      }
    val injector = Guice.createInjector(module)
    val asyncSwitch = injector.getInstance(AsyncSwitch::class.java) as FakeSwitch

    asyncSwitch.enable("async-feature")
    assertThat(asyncSwitch.isEnabled("async-feature")).isTrue()
  }

  @Test
  fun `getEnabledKeys returns copy not live set`() {
    fakeSwitch.enable("feature-a")
    val keys1 = fakeSwitch.getEnabledKeys()

    fakeSwitch.enable("feature-b")
    val keys2 = fakeSwitch.getEnabledKeys()

    // Original set should not have changed
    assertThat(keys1).containsExactly("feature-a")
    assertThat(keys2).containsExactlyInAnyOrder("feature-a", "feature-b")
  }

  @Test
  fun `works with ConditionalProvider`() {
    val module =
      object : KAbstractModule() {
        override fun configure() {
          bind<Switch>().to<FakeSwitch>()
          bind<String>()
            .toProvider(
              ConditionalProvider(
                switchKey = "test-feature",
                switchType = Switch::class,
                outputType = String::class,
                type = String::class,
                enabledInstance = "enabled",
                disabledInstance = "disabled",
              )
            )
        }
      }
    val injector = Guice.createInjector(module)
    val switch = injector.getInstance(Switch::class.java) as FakeSwitch
    val result = { injector.getInstance(String::class.java) }

    assertThat(result()).isEqualTo("disabled")

    switch.enable("test-feature")
    assertThat(result()).isEqualTo("enabled")

    switch.disable("test-feature")
    assertThat(result()).isEqualTo("disabled")
  }
}

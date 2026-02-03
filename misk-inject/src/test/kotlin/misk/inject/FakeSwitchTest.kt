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
    assertThat(fakeSwitch.enabledKeys).isEmpty()
  }

  @Test
  fun `can add key to enable it`() {
    fakeSwitch.enabledKeys.add("feature-a")

    assertThat(fakeSwitch.isEnabled("feature-a")).isTrue()
    assertThat(fakeSwitch.isDisabled("feature-a")).isFalse()
    assertThat(fakeSwitch.isEnabled("feature-b")).isFalse()
  }

  @Test
  fun `can remove key to disable it`() {
    fakeSwitch.enabledKeys.add("feature-a")
    fakeSwitch.enabledKeys.remove("feature-a")

    assertThat(fakeSwitch.isEnabled("feature-a")).isFalse()
  }

  @Test
  fun `can add multiple keys`() {
    fakeSwitch.enabledKeys.addAll(listOf("feature-a", "feature-b", "feature-c"))

    assertThat(fakeSwitch.isEnabled("feature-a")).isTrue()
    assertThat(fakeSwitch.isEnabled("feature-b")).isTrue()
    assertThat(fakeSwitch.isEnabled("feature-c")).isTrue()
  }

  @Test
  fun `can remove multiple keys`() {
    fakeSwitch.enabledKeys.addAll(listOf("feature-a", "feature-b", "feature-c"))
    fakeSwitch.enabledKeys.removeAll(listOf("feature-a", "feature-c"))

    assertThat(fakeSwitch.isEnabled("feature-a")).isFalse()
    assertThat(fakeSwitch.isEnabled("feature-b")).isTrue()
    assertThat(fakeSwitch.isEnabled("feature-c")).isFalse()
  }

  @Test
  fun `adding same key multiple times is idempotent`() {
    fakeSwitch.enabledKeys.add("feature-a")
    fakeSwitch.enabledKeys.add("feature-a")
    fakeSwitch.enabledKeys.add("feature-a")

    assertThat(fakeSwitch.isEnabled("feature-a")).isTrue()
    assertThat(fakeSwitch.enabledKeys).hasSize(1)
  }

  @Test
  fun `removing non-existent key is safe`() {
    fakeSwitch.enabledKeys.remove("feature-a")

    assertThat(fakeSwitch.isEnabled("feature-a")).isFalse()
  }

  @Test
  fun `ifEnabled executes block when key is enabled`() {
    fakeSwitch.enabledKeys.add("feature-a")
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
    fakeSwitch.enabledKeys.add("feature-a")
    var executed = false

    fakeSwitch.ifDisabled("feature-a") { executed = true }

    assertThat(executed).isFalse()
  }

  @Test
  fun `reset clears all enabled keys`() {
    fakeSwitch.enabledKeys.addAll(listOf("feature-a", "feature-b", "feature-c"))
    fakeSwitch.reset()

    assertThat(fakeSwitch.isEnabled("feature-a")).isFalse()
    assertThat(fakeSwitch.isEnabled("feature-b")).isFalse()
    assertThat(fakeSwitch.isEnabled("feature-c")).isFalse()
    assertThat(fakeSwitch.enabledKeys).isEmpty()
  }

  @Test
  fun `reset is called between tests when using MiskTest`() {
    // This test verifies that the fixture is properly reset between test runs
    // State from previous tests should not affect this test
    assertThat(fakeSwitch.enabledKeys).isEmpty()
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

    asyncSwitch.enabledKeys.add("async-feature")
    assertThat(asyncSwitch.isEnabled("async-feature")).isTrue()
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

    switch.enabledKeys.add("test-feature")
    assertThat(result()).isEqualTo("enabled")

    switch.enabledKeys.remove("test-feature")
    assertThat(result()).isEqualTo("disabled")
  }
}

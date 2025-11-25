package misk.inject

import com.google.inject.Guice
import misk.testing.MiskTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest
class AsyncSwitchTest {
  @Test
  fun alwaysEnabledSwitchIsEnabled() {
    val switch = AlwaysEnabledSwitch()
    assertThat(switch.isEnabled("any-key")).isTrue()
    assertThat(switch.isDisabled("any-key")).isFalse()
  }

  @Test
  fun switchIfEnabledExecutesBlockWhenEnabled() {
    val switch = AlwaysEnabledSwitch()
    var executed = false
    switch.ifEnabled("key") { executed = true }
    assertThat(executed).isTrue()
  }

  @Test
  fun switchIfEnabledDoesNotExecuteBlockWhenDisabled() {
    val switch = object : Switch {
      override fun isEnabled(key: String) = false
    }
    var executed = false
    switch.ifEnabled("key") { executed = true }
    assertThat(executed).isFalse()
  }

  @Test
  fun switchIfDisabledExecutesBlockWhenDisabled() {
    val switch = object : Switch {
      override fun isEnabled(key: String) = false
    }
    var executed = false
    switch.ifDisabled("key") { executed = true }
    assertThat(executed).isTrue()
  }

  @Test
  fun switchIfDisabledDoesNotExecuteBlockWhenEnabled() {
    val switch = AlwaysEnabledSwitch()
    var executed = false
    switch.ifDisabled("key") { executed = true }
    assertThat(executed).isFalse()
  }

  @Test
  fun defaultAsyncSwitchModuleBindsAlwaysOnByDefault() {
    val injector = Guice.createInjector(DefaultAsyncSwitchModule())
    val asyncSwitch = injector.getInstance(AsyncSwitch::class.java)
    assertThat(asyncSwitch.isEnabled("any-key")).isTrue()
  }

  @Test
  fun customAsyncSwitchCanBeProvided() {
    val customSwitch = object : AsyncSwitch {
      override fun isEnabled(key: String) = key == "enabled-key"
    }
    val moduleWithDefaultModule = object : KAbstractModule() {
      override fun configure() {
        install(DefaultAsyncSwitchModule())
        bindOptionalBinding<AsyncSwitch>().toInstance(customSwitch)
      }
    }
    val injector = Guice.createInjector(moduleWithDefaultModule)
    val asyncSwitch = injector.getInstance(AsyncSwitch::class.java)
    assertThat(asyncSwitch.isEnabled("enabled-key")).isTrue()
    assertThat(asyncSwitch.isEnabled("other-key")).isFalse()
  }

  @Test
  fun conditionalProviderReturnsEnabledInstanceWhenEnabled() {
    val module = object : KAbstractModule() {
      override fun configure() {
        bind<AsyncSwitch>().toInstance(AlwaysEnabledSwitch())
        bind<String>().toProvider(
          ConditionalProvider(
            switchKey = "test-key",
            switchType = AsyncSwitch::class,
            outputType = String::class,
            type = String::class,
            enabledInstance = "enabled",
            disabledInstance = "disabled"
          )
        )
      }
    }
    val injector = Guice.createInjector(module)
    val result = injector.getInstance(String::class.java)
    assertThat(result).isEqualTo("enabled")
  }

  @Test
  fun conditionalProviderReturnsDisabledInstanceWhenDisabled() {
    val customSwitch = object : AsyncSwitch {
      override fun isEnabled(key: String) = false
    }
    val module = object : KAbstractModule() {
      override fun configure() {
        bind<AsyncSwitch>().toInstance(customSwitch)
        bind<String>().toProvider(
          ConditionalProvider(
            switchKey = "test-key",
            switchType = AsyncSwitch::class,
            outputType = String::class,
            type = String::class,
            enabledInstance = "enabled",
            disabledInstance = "disabled"
          )
        )
      }
    }
    val injector = Guice.createInjector(module)
    val result = injector.getInstance(String::class.java)
    assertThat(result).isEqualTo("disabled")
  }

  @Test
  fun conditionalProviderWithTransformer() {
    val module = object : KAbstractModule() {
      override fun configure() {
        bind<AsyncSwitch>().toInstance(AlwaysEnabledSwitch())
        bind<Int>().toProvider(
          ConditionalProvider(
            switchKey = "test-key",
            switchType = AsyncSwitch::class,
            outputType = Int::class,
            type = String::class,
            enabledInstance = "42",
            disabledInstance = "0",
            transformer = { it.toInt() }
          )
        )
      }
    }
    val injector = Guice.createInjector(module)
    val result = injector.getInstance(Int::class.java)
    assertThat(result).isEqualTo(42)
  }

  @Test
  fun conditionalTypedProviderReturnsEnabledInstanceWhenEnabled() {
    val module = object : KAbstractModule() {
      override fun configure() {
        bind<AsyncSwitch>().toInstance(AlwaysEnabledSwitch())
        bind<Shape>().toInstance(Square())
        bind<Shape>().annotatedWith<TestAnnotation>().toInstance(Circle())
        bind<Shape>().annotatedWith<TestAnnotation2>().toProvider(
          ConditionalTypedProvider<AsyncSwitch, Shape, Shape, Square, Circle>(
            switchKey = "test-key",
          )
        )
      }
    }
    val injector = Guice.createInjector(module)
    val result = injector.getInstance(
      com.google.inject.Key.get(
        Shape::class.java,
        TestAnnotation2::class.java
      )
    )
    assertThat(result).isInstanceOf(Square::class.java)
  }

  @Test
  fun conditionalTypedProviderUsesCustomSwitchWhenEnabled() {
    val customSwitch = object : Switch {
      override fun isEnabled(key: String) = key == "custom-enabled-key"
    }
    val module = object : KAbstractModule() {
      override fun configure() {
        bind<Switch>().toInstance(customSwitch)
        bind<Shape>().toInstance(Square())
        bind<Shape>().annotatedWith<TestAnnotation>().toInstance(Circle())
        bind<Color>().toProvider(
          ConditionalTypedProvider<Switch, Color, Color, Blue, Red>(
            switchKey = "custom-enabled-key",
          )
        )
      }
    }
    val injector = Guice.createInjector(module)
    val result = injector.getInstance(Color::class.java)
    assertThat(result).isInstanceOf(Blue::class.java)
  }

  @Test
  fun conditionalTypedProviderUsesCustomSwitchWhenDisabled() {
    val customSwitch = object : Switch {
      override fun isEnabled(key: String) = key == "different-key"
    }
    val module = object : KAbstractModule() {
      override fun configure() {
        bind<Switch>().toInstance(customSwitch)
        bind<Shape>().toInstance(Square())
        bind<Shape>().annotatedWith<TestAnnotation>().toInstance(Circle())
        bind<Color>().toProvider(
          ConditionalTypedProvider<Switch, Color, Color, Blue, Red>(
            switchKey = "custom-enabled-key",
          )
        )
      }
    }
    val injector = Guice.createInjector(module)
    val result = injector.getInstance(Color::class.java)
    assertThat(result).isInstanceOf(Red::class.java)
  }

  @Test fun conditionalTypedProviderWithAsyncSwitchRespectsSwitchState() {
    val customSwitch = object : AsyncSwitch {
      override fun isEnabled(key: String) = key.startsWith("enabled")
    }
    val module = object : KAbstractModule() {
      override fun configure() {
        bind<AsyncSwitch>().toInstance(customSwitch)
        bind<Shape>().toInstance(Square())
        bind<Shape>().annotatedWith<TestAnnotation>().toInstance(Circle())

        // Test with enabled key
        bind<Color>().annotatedWith<TestAnnotation>().toProvider(
          ConditionalTypedProvider<AsyncSwitch, Color, Color, Blue, Red>(
            switchKey = "enabled-feature",
          )
        )

        // Test with disabled key
        bind<Color>().annotatedWith<TestAnnotation2>().toProvider(
          ConditionalTypedProvider<AsyncSwitch, Color, Color, Blue, Red>(
            switchKey = "disabled-feature"
          )
        )
      }
    }
    val injector = Guice.createInjector(module)

    val enabledResult = injector.getInstance(
      com.google.inject.Key.get(
        Color::class.java,
        TestAnnotation::class.java
      )
    )
    assertThat(enabledResult).isInstanceOf(Blue::class.java)

    val disabledResult = injector.getInstance(
      com.google.inject.Key.get(
        Color::class.java,
        TestAnnotation2::class.java
      )
    )
    assertThat(disabledResult).isInstanceOf(Red::class.java)
  }
}
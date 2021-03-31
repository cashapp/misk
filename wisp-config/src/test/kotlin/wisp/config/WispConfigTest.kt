package wisp.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class WispConfigTest {

  data class MyConfig(
    val foo: Foo,
    val baz: String
  ) : Config

  data class Foo(
    val enabled: Boolean,
    val bar: Int
  )

  @Test
  fun `config source for a single config file loads`() {
    val builder = WispConfig.builder()
    builder.addWispConfigSources(
      listOf(ConfigSource("classpath:/a.yaml"))
    )

    val myConfig = builder.build().loadConfigOrThrow<MyConfig>()
    assertTrue(myConfig.foo.enabled)
    assertEquals(72, myConfig.foo.bar)
    assertEquals("abc", myConfig.baz)
  }

  @Test
  fun `config source for a two config file loads with correct override`() {
    val builder = WispConfig.builder()
    builder.addWispConfigSources(
      listOf(
        ConfigSource("classpath:/b.yaml"),
        ConfigSource("classpath:/a.yaml")
      )
    )

    val myConfig = builder.build().loadConfigOrThrow<MyConfig>()
    assertFalse(myConfig.foo.enabled)
    assertEquals(72, myConfig.foo.bar)
    assertEquals("abc", myConfig.baz)
  }

  @Test
  fun `config source for a multiple config file loads with correct override`() {
    val builder = WispConfig.builder()
    builder.addWispConfigSources(
      listOf(
        ConfigSource("classpath:/c.yaml"),
        ConfigSource("classpath:/b.yaml"),
        ConfigSource("classpath:/a.yaml")
      )
    )

    val myConfig = builder.build().loadConfigOrThrow<MyConfig>()
    assertFalse(myConfig.foo.enabled)
    assertEquals(11, myConfig.foo.bar)
    assertEquals("xyz", myConfig.baz)
  }
}

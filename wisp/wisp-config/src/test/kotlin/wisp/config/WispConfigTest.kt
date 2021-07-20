package wisp.config

import com.sksamuel.hoplite.Masked
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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

  data class AWSCredentials (
    val AWS_ACCESS_KEY_ID: Masked,
    val AWS_SECRET_ACCESS_KEY: Masked
  ) : Config

  data class AWSConfigYaml (
    val aws: AWSCredentials
  ) : Config

  @Test
  fun `config for a single config file loads`() {
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
  fun `config for a two config file loads with correct override`() {
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
  fun `config for a multiple config files loads with correct override`() {
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

  @Test
  fun `config for a multiple config files with some not existing, loads with correct override`() {
    val builder = WispConfig.builder()
    builder.addWispConfigSources(
      listOf(
        ConfigSource("classpath:/do_not_exist.yaml"),
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


  @Test
  fun `config for a aws credential yaml file loads with correct values and should be masked`() {
    val builder = WispConfig.builder()
    builder.addWispConfigSources(
      listOf(
        ConfigSource("classpath:/aws.yaml"),
      )
    )

    val myConfig = builder.build().loadConfigOrThrow<AWSConfigYaml>()
    assertEquals("AAAAAAAAAAAAAAAA", myConfig.aws.AWS_ACCESS_KEY_ID.value)
    assertEquals("RRRRRRRRRRRRRRRRR", myConfig.aws.AWS_SECRET_ACCESS_KEY.value)

    // values should me masked
    assertNotEquals("AAAAAAAAAAAAAAAA", myConfig.aws.AWS_ACCESS_KEY_ID.toString())
    assertNotEquals("RRRRRRRRRRRRRRRRR", myConfig.aws.AWS_SECRET_ACCESS_KEY.toString())
  }

  @Test
  fun `config for a aws credential properties file loads with correct values and should be masked`() {
    val builder = WispConfig.builder()
    builder.addWispConfigSources(
      listOf(
        ConfigSource("classpath:/aws_credentials", "props"),
      )
    )

    val myConfig = builder.build().loadConfigOrThrow<AWSCredentials>()
    assertEquals("AAAAAAAAAAAAAAAA", myConfig.AWS_ACCESS_KEY_ID.value)
    assertEquals("RRRRRRRRRRRRRRRRR", myConfig.AWS_SECRET_ACCESS_KEY.value)

    // values should me masked
    assertNotEquals("AAAAAAAAAAAAAAAA", myConfig.AWS_ACCESS_KEY_ID.toString())
    assertNotEquals("RRRRRRRRRRRRRRRRR", myConfig.AWS_SECRET_ACCESS_KEY.toString())
  }

  // TODO(chrisryan): add tests to support other formats
}

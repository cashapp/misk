package misk.flags

import com.google.inject.name.Named
import misk.MiskTestingServiceModule
import misk.flags.memory.InMemoryFlagStore
import misk.flags.memory.InMemoryFlagStoreModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class FlagPropertiesTest {

  data class JsonData(val message: String)

  class MyFlags internal constructor(context: Flags.Context) : Flags("my-flags", context) {
    val string by StringFlag("This is the string flag", default = "defValue")
    val int by IntFlag("this is the int flag", default = 101)
    val boolean by BooleanFlag("this is the boolean flag", default = true)
    val double by DoubleFlag("this is the double flag", default = 100.45)
    val json by JsonFlag<JsonData>(description = "this is the json flag")
  }

  @MiskTestModule
  val testModule = object : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(InMemoryFlagStoreModule())
      install(object : FlagsModule() {
        override fun configureFlags() {
          bindFlags<MyFlags>()
          bindFlags<MyFlags>("unique-prefix")
        }
      })
    }
  }

  @Inject @Named("unique-prefix") private lateinit var prefixedFlags: MyFlags

  @Inject private lateinit var nakedFlags: MyFlags

  @Inject private lateinit var flagStore: InMemoryFlagStore

  @Test
  fun flagPropertiesRegisterCorrectFlags() {
    assertThat(flagStore.stringFlags.keys).containsExactlyInAnyOrder(
        "unique-prefix.my-flags.string",
        "unique-prefix.my-flags.json",
        "my-flags.string",
        "my-flags.json")
    assertThat(flagStore.intFlags.keys).containsExactlyInAnyOrder(
        "unique-prefix.my-flags.int",
        "my-flags.int")
    assertThat(flagStore.booleanFlags.keys).containsExactlyInAnyOrder(
        "unique-prefix.my-flags.boolean",
        "my-flags.boolean")
    assertThat(flagStore.doubleFlags.keys).containsExactlyInAnyOrder(
        "unique-prefix.my-flags.double",
        "my-flags.double")
  }

  @Test
  fun flagPropertiesUseDefaults() {
    assertThat(nakedFlags.string).isEqualTo("defValue")
    assertThat(nakedFlags.int).isEqualTo(101)
    assertThat(nakedFlags.boolean).isTrue()
    assertThat(nakedFlags.double).isCloseTo(100.45, Offset.offset(0.0001))
    assertThat(nakedFlags.json).isNull()
  }

  @Test
  fun flagPropertiesRespondToFlagChanges() {
    flagStore.stringFlags["my-flags.string"]!!.set("new-value")
    flagStore.booleanFlags["my-flags.boolean"]!!.set(false)
    flagStore.doubleFlags["my-flags.double"]!!.set(285.6)
    flagStore.stringFlags["my-flags.json"]!!.set("""{"message":"yo!"}""")
    flagStore.intFlags["my-flags.int"]!!.set(10001)

    // Flag properties should pick up changes
    assertThat(nakedFlags.string).isEqualTo("new-value")
    assertThat(nakedFlags.int).isEqualTo(10001)
    assertThat(nakedFlags.boolean).isFalse()
    assertThat(nakedFlags.double).isCloseTo(285.6, Offset.offset(0.0001))
    assertThat(nakedFlags.json).isEqualTo(JsonData("yo!"))

    // Other flag instance should not be affected
    assertThat(prefixedFlags.string).isEqualTo("defValue")
    assertThat(prefixedFlags.int).isEqualTo(101)
    assertThat(prefixedFlags.boolean).isTrue()
    assertThat(prefixedFlags.double).isCloseTo(100.45, Offset.offset(0.0001))
    assertThat(prefixedFlags.json).isNull()
  }
}

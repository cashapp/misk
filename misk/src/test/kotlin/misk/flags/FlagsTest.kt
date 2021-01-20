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
class FlagsTest {
  data class JsonData(val message: String)

  @MiskTestModule
  val testModule = object : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(InMemoryFlagStoreModule())
      install(object : FlagsModule() {
        override fun configureFlags() {
          bindFlag<Boolean>("my-bool", "this is the boolean flag")
          bindFlag<Double>("my-double", "this is the double flag")
          bindFlag<String>("my-string", "this is the string flag")
          bindFlag<Int>("my-int", "this is the int flag")
          bindJsonFlag<JsonData>("my-json", "this is the json flag")

          bindFlag<Boolean>("my-other-bool", "this is the other boolean flag")
          bindFlag<Double>("my-other-double", "this is the other double flag")
          bindFlag<String>("my-other-string", "this is the other string flag")
          bindFlag<Int>("my-other-int", "this is the other int flag")
          bindJsonFlag<JsonData>("my-other-json", "this is the other json flag")
        }
      })
    }
  }

  @Inject @Named("my-bool") private lateinit var boolean: Flag<Boolean>

  @Inject @Named("my-string") private lateinit var string: Flag<String>

  @Inject @Named("my-int") private lateinit var int: Flag<Int>

  @Inject @Named("my-double") private lateinit var double: Flag<Double>

  @Inject @Named("my-json") private lateinit var json: JsonFlag<JsonData>

  @Inject @Named("my-other-bool") private lateinit var otherBoolean: Flag<Boolean>

  @Inject @Named("my-other-string") private lateinit var otherString: Flag<String>

  @Inject @Named("my-other-int") private lateinit var otherInt: Flag<Int>

  @Inject @Named("my-other-double") private lateinit var otherDouble: Flag<Double>

  @Inject @Named("my-other-json") private lateinit var otherJson: JsonFlag<JsonData>

  @Inject private lateinit var flagStore: InMemoryFlagStore

  @Test
  fun flagsRegistered() {
    assertThat(flagStore.booleanFlags["my-bool"]!!.description)
        .isEqualTo("this is the boolean flag")
    assertThat(flagStore.intFlags["my-int"]!!.description)
        .isEqualTo("this is the int flag")
    assertThat(flagStore.stringFlags["my-string"]!!.description)
        .isEqualTo("this is the string flag")
    assertThat(flagStore.stringFlags["my-json"]!!.description)
        .isEqualTo("this is the json flag")
    assertThat(flagStore.doubleFlags["my-double"]!!.description)
        .isEqualTo("this is the double flag")

    assertThat(flagStore.booleanFlags["my-other-bool"]!!.description)
        .isEqualTo("this is the other boolean flag")
    assertThat(flagStore.intFlags["my-other-int"]!!.description)
        .isEqualTo("this is the other int flag")
    assertThat(flagStore.stringFlags["my-other-string"]!!.description)
        .isEqualTo("this is the other string flag")
    assertThat(flagStore.stringFlags["my-other-json"]!!.description)
        .isEqualTo("this is the other json flag")
    assertThat(flagStore.doubleFlags["my-other-double"]!!.description)
        .isEqualTo("this is the other double flag")
  }

  @Test
  fun flagsNullByDefault() {
    assertThat(boolean.get()).isNull()
    assertThat(string.get()).isNull()
    assertThat(int.get()).isNull()
    assertThat(double.get()).isNull()
    assertThat(json.get()).isNull()

    assertThat(otherBoolean.get()).isNull()
    assertThat(otherString.get()).isNull()
    assertThat(otherInt.get()).isNull()
    assertThat(otherDouble.get()).isNull()
    assertThat(otherJson.get()).isNull()
  }

  @Test
  fun flagChangesExposed() {
    flagStore.booleanFlags["my-bool"]!!.set(true)
    flagStore.intFlags["my-int"]!!.set(200)
    flagStore.stringFlags["my-string"]!!.set("yo!")
    flagStore.stringFlags["my-json"]!!.set("""{"message":"hello!"}""")
    flagStore.doubleFlags["my-double"]!!.set(272.345)

    assertThat(boolean.get()).isTrue()
    assertThat(string.get()).isEqualTo("yo!")
    assertThat(int.get()).isEqualTo(200)
    assertThat(double.get()).isCloseTo(272.345, Offset.offset(0.0001))
    assertThat(json.get()).isEqualTo(JsonData("hello!"))

    assertThat(otherBoolean.get()).isNull()
    assertThat(otherString.get()).isNull()
    assertThat(otherInt.get()).isNull()
    assertThat(otherDouble.get()).isNull()
    assertThat(otherJson.get()).isNull()
  }
}

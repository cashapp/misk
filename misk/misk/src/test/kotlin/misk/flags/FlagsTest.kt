package misk.flags

import com.google.inject.name.Named
import misk.flags.memory.InMemoryFlagStore
import misk.flags.memory.InMemoryFlagStoreModule
import misk.inject.KAbstractModule
import misk.moshi.MoshiModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = false)
class FlagsTest {
  data class JsonData(val message: String)

  @MiskTestModule
  val testModule = object : KAbstractModule() {
    override fun configure() {
      install(MoshiModule())
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

  @Inject
  private lateinit
  @Named("my-bool")
  var boolean: Flag<Boolean>

  @Inject
  private lateinit
  @Named("my-string")
  var string: Flag<String>

  @Inject
  private lateinit
  @Named("my-int")
  var int: Flag<Int>

  @Inject
  private lateinit
  @Named("my-double")
  var double: Flag<Double>

  @Inject
  private lateinit
  @Named("my-json")
  var json: JsonFlag<JsonData>

  @Inject
  private lateinit
  @Named("my-other-bool")
  var otherBoolean: Flag<Boolean>

  @Inject
  private lateinit
  @Named("my-other-string")
  var otherString: Flag<String>

  @Inject
  private lateinit
  @Named("my-other-int")
  var otherInt: Flag<Int>

  @Inject
  private lateinit
  @Named("my-other-double")
  var otherDouble: Flag<Double>

  @Inject
  private lateinit
  @Named("my-other-json")
  var otherJson: JsonFlag<JsonData>

  @Inject
  private lateinit
  var flagStore: InMemoryFlagStore

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

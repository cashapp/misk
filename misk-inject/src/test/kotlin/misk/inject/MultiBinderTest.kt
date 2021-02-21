package misk.inject

import com.google.inject.name.Names
import javax.inject.Inject
import javax.inject.Named
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest
class MultiBinderTest {
  @MiskTestModule
  val module = MultiBindingsModule()

  @Inject @TestAnnotation private lateinit var intList: List<Int>
  @Inject @TestAnnotation private lateinit var intSet: Set<Int>

  @Inject @TestAnnotation private lateinit var colorList: List<Color>
  @Inject @TestAnnotation private lateinit var colorSet: Set<Color>

  @Inject @field:Named("hello") private lateinit var namedIntList: List<Int>
  @Inject @field:Named("hello") private lateinit var namedIntSet: Set<Int>

  @Inject private lateinit var unqualifiedColorList: List<Color>
  @Inject private lateinit var unqualifiedColorSet: Set<Color>

  @Test
  fun testMultibinder() {
    assertThat(intList).hasSize(1)
    assertThat(intSet).hasSize(1)

    assertThat(colorList).hasSize(2)
    assertThat(colorSet).hasSize(2)

    assertThat(namedIntList).hasSize(3)
    assertThat(namedIntSet).hasSize(3)

    assertThat(unqualifiedColorList).hasSize(1)
    assertThat(unqualifiedColorSet).hasSize(1)
  }
}

class MultiBindingsModule : KAbstractModule() {
  override fun configure() {
    // One annotated Int.
    newMultibinder<Int>(TestAnnotation::class).addBinding().toInstance(1)

    // Two annotated colors.
    newMultibinder<Color>(TestAnnotation::class).addBinding().toInstance(Blue())
    multibind<Color>(TestAnnotation::class).toInstance(Red())

    // Three named ints.
    newMultibinder<Int>(Names.named("hello")).addBinding().toInstance(1)
    multibind<Int>(Names.named("hello")).toInstance(2)
    multibind<Int>(Names.named("hello")).toInstance(3)

    // One unannotated color.
    newMultibinder<Color>().addBinding().toInstance(Blue())
  }
}

package misk.inject

import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class MultiBinderTest {
  @MiskTestModule
  val module = MultiBindingsModule()

  @Inject @TestAnnotation private lateinit var colorList: List<Color>
  @Inject @TestAnnotation private lateinit var colorSet: Set<Color>
  @Inject @TestAnnotation private lateinit var intList: List<Int>
  @Inject @TestAnnotation private lateinit var intSet: Set<Int>

  @Inject private lateinit var unqualifiedColorList: List<Color>
  @Inject private lateinit var unqualifiedColorSet: Set<Color>

  @Test
  fun testMultibinder() {
    assertThat(colorList).hasSize(2)
    assertThat(colorSet).hasSize(2)
    assertThat(intList).hasSize(1)
    assertThat(intSet).hasSize(1)
    assertThat(unqualifiedColorList).hasSize(1)
    assertThat(unqualifiedColorSet).hasSize(1)
  }
}

class MultiBindingsModule : KAbstractModule() {
  override fun configure() {
    newMultibinder<Int>(TestAnnotation::class).addBinding().toInstance(1)

    newMultibinder<Color>(TestAnnotation::class).addBinding().toInstance(
      Blue()
    )
    newMultibinder<Color>(TestAnnotation::class).addBinding().toInstance(
      Red()
    )

    newMultibinder<Color>().addBinding().toInstance(Blue())
  }
}

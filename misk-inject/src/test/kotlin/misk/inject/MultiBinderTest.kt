package misk.inject

import com.google.inject.TypeLiteral
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import jakarta.inject.Inject

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
  
  @Inject private lateinit var genericTypeList: List<ColorWrapper<*>>
  @Inject private lateinit var genericTypeSet: Set<ColorWrapper<*>>

  @Test
  fun testMultibinder() {
    assertThat(colorList).hasSize(2)
    assertThat(colorSet).hasSize(2)
    assertThat(intList).hasSize(1)
    assertThat(intSet).hasSize(1)

    assertThat(unqualifiedColorList).hasSize(1)
    assertThat(unqualifiedColorSet).hasSize(1)

    assertThat(genericTypeList).hasSize(2)
    assertThat(genericTypeSet).hasSize(2)
  }
}

class MultiBindingsModule : KAbstractModule() {
  override fun configure() {
    // One annotated Int.
    newMultibinder<Int>(TestAnnotation::class).addBinding().toInstance(1)

    // Two annotated colors.
    newMultibinder<Color>(TestAnnotation::class).addBinding().toInstance(Blue())
    newMultibinder<Color>(TestAnnotation::class).addBinding().toInstance(Red())

    // One unannotated color.
    newMultibinder<Color>().addBinding().toInstance(Blue())

    // Two colors wrapped in a generic type
    val wrapperType = object : TypeLiteral<ColorWrapper<*>>() {}
    newMultibinder(wrapperType).addBinding().toInstance(ColorWrapper.Blue())
    newMultibinder(wrapperType).addBinding().toInstance(ColorWrapper.Red())
  }
}

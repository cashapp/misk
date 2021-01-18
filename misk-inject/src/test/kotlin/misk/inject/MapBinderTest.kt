package misk.inject

import misk.ServiceManagerModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Qualifier

@MiskTest
class MapBinderTest {
  @MiskTestModule
  val module = TestModule()

  @Inject @TestAnnotation private lateinit var intToName: Map<Int, String>
  @Inject @TestAnnotation private lateinit var nameToShape: Map<String, Shape>
  @Inject @TestAnnotation private lateinit var shapeToName: Map<Shape, String>
  @Inject @TestAnnotation
  private lateinit var shapeToColor: Map<Shape, Color>
  @Inject private lateinit var unqualifiedShapeToColor: Map<Shape, Color>

  @Test
  fun testMapbinder() {
    assertThat(intToName).hasSize(2)
    assertThat(nameToShape).hasSize(2)
    assertThat(shapeToName).hasSize(2)
    assertThat(shapeToColor).hasSize(2)
    assertThat(unqualifiedShapeToColor).hasSize(2)
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD)
annotation class TestAnnotation

interface Shape
class Square : Shape
class Circle : Shape

interface Color
class Blue : Color
class Red : Color

class TestModule : KAbstractModule() {
  override fun configure() {
    install(ServiceManagerModule())
    newMapBinder<Int, String>(TestAnnotation::class).addBinding(1).toInstance("one")
    newMapBinder<Int, String>(TestAnnotation::class).addBinding(2).toInstance("two")

    newMapBinder<String, Shape>(TestAnnotation::class).addBinding("square").toInstance(
        Square())
    newMapBinder<String, Shape>(TestAnnotation::class).addBinding("circle").toInstance(
        Circle())

    newMapBinder<Shape, String>(TestAnnotation::class).addBinding(
        Square()).toInstance("square")
    newMapBinder<Shape, String>(TestAnnotation::class).addBinding(
        Circle()).toInstance("circle")

    newMapBinder<Shape, Color>(TestAnnotation::class).addBinding(
        Square()).toInstance(Blue())
    newMapBinder<Shape, Color>(TestAnnotation::class).addBinding(
        Circle()).toInstance(Red())

    newMapBinder<Shape, Color>().addBinding(Square()).toInstance(
        Blue())
    newMapBinder<Shape, Color>().addBinding(Circle()).toInstance(
        Red())
  }
}
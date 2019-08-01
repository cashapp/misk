package misk.web.actions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ReflectTest {
  @Test
  internal fun classSuperclasses() {
    assertThat(Square::class.java.superclasses()).containsExactly(
        Square::class.java,
        Polygon::class.java,
        Territory::class.java,
        Object::class.java,
        Shape::class.java
    )
  }

  @Test
  internal fun overriddenMethods() {
    val square = Square::class.java
    val polygon = Polygon::class.java
    val territory = Territory::class.java
    val shape = Shape::class.java
    val int = Int::class.java

    assertThat(square.getDeclaredMethod("area").overrides()).containsExactly(
        square.getDeclaredMethod("area"),
        territory.getDeclaredMethod("area"),
        shape.getDeclaredMethod("area")
    )
    assertThat(square.getDeclaredMethod("perimeter").overrides()).containsExactly(
        square.getDeclaredMethod("perimeter"),
        shape.getDeclaredMethod("perimeter")
    )
    assertThat(square.getDeclaredMethod("contains", int, int).overrides()).containsExactly(
        square.getDeclaredMethod("contains", int, int),
        territory.getDeclaredMethod("contains", int, int)
    )
    assertThat(square.getDeclaredMethod("edgeCount").overrides()).containsExactly(
        square.getDeclaredMethod("edgeCount"),
        polygon.getDeclaredMethod("edgeCount"),
        shape.getDeclaredMethod("edgeCount")
    )
    assertThat(square.getDeclaredMethod("toString").overrides()).containsExactly(
        square.getDeclaredMethod("toString"),
        Object::class.java.getDeclaredMethod("toString")
    )
  }

  @Test
  internal fun annotationWithOverrides() {
    assertThat(Square::class.java.getDeclaredMethod("area")
        .findAnnotationWithOverrides(Tag::class.java)!!.name).isEqualTo("square")
    assertThat(Square::class.java.getDeclaredMethod("perimeter")
        .findAnnotationWithOverrides(Tag::class.java)).isNull()
    assertThat(Square::class.java.getDeclaredMethod("edgeCount")
        .findAnnotationWithOverrides(Tag::class.java)!!.name).isEqualTo("polygon")
  }

  class Square : Polygon(), Territory {
    @Tag("square") override fun area() = error("unused")
    override fun perimeter() = error("unused")
    override fun contains(x: Int, y: Int) = error("unused")
    override fun edgeCount() = error("unused")
    override fun toString() = error("unused")
  }

  abstract class Polygon : Shape {
    @Tag("polygon") abstract override fun edgeCount(): Int
  }

  interface Shape {
    @Tag("shape") fun area(): Long
    fun perimeter(): Long
    @Tag("shape") fun edgeCount(): Int
  }

  interface Territory {
    @Tag("territory") fun area(): Long
    fun contains(x: Int, y: Int): Boolean
  }

  @Target(AnnotationTarget.FUNCTION)
  annotation class Tag(val name: String)
}
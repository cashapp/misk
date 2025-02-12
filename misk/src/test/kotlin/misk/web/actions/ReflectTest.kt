package misk.web.actions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.javaMethod

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
    val void = Void::class.java
    val boolean = Boolean::class.java
    val int = Int::class.java
    val long = Long::class.java
    val string = String::class.java

    assertThat(square.declaredMethod("area", long).overrides()).containsExactly(
      square.declaredMethod("area", void),
      territory.declaredMethod("area", long),
      shape.declaredMethod("area", long)
    )
    assertThat(square.declaredMethod("perimeter", long).overrides()).containsExactly(
      square.declaredMethod("perimeter", void),
      shape.declaredMethod("perimeter", long)
    )
    assertThat(square.declaredMethod("contains", boolean, int, int).overrides()).containsExactly(
      square.declaredMethod("contains", void, int, int),
      territory.declaredMethod("contains", boolean, int, int)
    )
    assertThat(square.declaredMethod("edgeCount", int).overrides()).containsExactly(
      square.declaredMethod("edgeCount", void),
      polygon.declaredMethod("edgeCount", int),
      shape.declaredMethod("edgeCount", int)
    )
    assertThat(square.declaredMethod("toString", void).overrides()).containsExactly(
      square.declaredMethod("toString", void),
      Object::class.java.declaredMethod("toString", string)
    )
  }

  @Test
  internal fun annotationWithOverrides() {
    assertThat(
      Square::class.functions.first { it.name == "area" }
        .withOverrides()
        .findAnnotation<Tag>()!!.name
    ).isEqualTo("square")
    assertThat(
      Square::class.functions.first { it.name == "perimeter" }
        .withOverrides()
        .findAnnotation<Tag>()
    ).isNull()
    assertThat(
      Square::class.functions.first { it.name == "edgeCount" }
        .withOverrides()
        .findAnnotation<Tag>()!!.name
    ).isEqualTo("polygon")
  }

  @Test
  internal fun parameterAnnotationWithOverridesRequestWithoutValue() {
    val greetEmptyFunction = RealGreetingAction::class.functions.single { it.name == "greetEmpty" }
      .withOverrides()
    assertThat(greetEmptyFunction.findAnnotation<Tag>()!!.name).isEqualTo("/greet-empty")

    val messageEmptyParameter = greetEmptyFunction.parameters.single { it.name == "message"  }
    assertThat(messageEmptyParameter.findAnnotation<RequestEmpty>()).isNotNull()

    assertThat(greetEmptyFunction.callBy(
      mapOf(
        greetEmptyFunction.parameters[0] to RealGreetingAction(),
        greetEmptyFunction.parameters[1] to "hello",
      )
    )).isEqualTo("806")
  }

  @Test
  internal fun parameterAnnotationWithOverridesParameterWithValue() {
    val greetFunction = RealGreetingAction::class.functions.single { it.name == "greet" }
      .withOverrides()
    assertThat(greetFunction.findAnnotation<Tag>()!!.name).isEqualTo("/greet")

    val messageParameter = greetFunction.parameters.single { it.name == "message"  }
    assertThat(messageParameter.findAnnotation<RequestPayload>()!!.name).isEqualTo("hello")

    assertThat(greetFunction.callBy(
      mapOf(
        greetFunction.parameters[0] to RealGreetingAction(),
        greetFunction.parameters[1] to "hello",
      )
    )).isEqualTo("33")
  }

  @Target(AnnotationTarget.VALUE_PARAMETER)
  annotation class RequestPayload(val name: String)

  @Target(AnnotationTarget.VALUE_PARAMETER)
  annotation class RequestEmpty

  interface GreetingAction {
    @Tag("/greet")
    fun greet(@RequestPayload("hello") message: String): String

    @Tag("/greet-empty")
    fun greetEmpty(@RequestEmpty message: String): String
  }

  class RealGreetingAction:GreetingAction {
    override fun greet(message: String): String  = "33"
    override fun greetEmpty(message: String): String = "806"
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

  @Test
  internal fun preferNonSyntheticReturnsNonSynthetic() {
    val methodsByReturnType = SquareGenerator::class.java.declaredMethods.associateBy {
      it.returnType
    }

    val returnsAny = methodsByReturnType[Any::class.java]!!
    assertThat(returnsAny.isSynthetic).isTrue()

    val returnsSquare = methodsByReturnType[Square::class.java]!!
    assertThat(returnsSquare.isSynthetic).isFalse()

    assertThat(returnsAny.preferNonSynthetic()).isEqualTo(returnsSquare)
    assertThat(returnsSquare.preferNonSynthetic()).isEqualTo(returnsSquare)
  }

  interface Generator<T : Any> {
    fun generate(): T
  }

  class SquareGenerator : Generator<Square> {
    override fun generate() = Square()
  }

  private fun Class<*>.declaredMethod(
    name: String,
    returnType: Class<*>,
    vararg argumentTypes: Class<*>
  ): Method {
    return declaredMethods.first {
      it.name == name &&
        it.returnType == returnType &&
        it.parameterTypes.contentEquals(argumentTypes)
    }
  }
}

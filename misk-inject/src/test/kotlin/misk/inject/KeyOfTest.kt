package misk.inject

import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import com.google.inject.name.Names
import java.lang.reflect.ParameterizedType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KeyOfTest {
  @Test
  fun `keyOf with simple type creates key`() {
    val key = keyOf<String>()
    assertThat(key).isEqualTo(Key.get(String::class.java))
  }

  @Test
  fun `keyOf with simple type preserves type via TypeLiteral`() {
    val key = keyOf<String>()
    // TypeLiteral-based key and class-based key are equal for non-generic types.
    assertThat(key).isEqualTo(Key.get(object : TypeLiteral<String>() {}))
  }

  @Test
  fun `keyOf with fully specified generic type preserves type parameters`() {
    val key = keyOf<List<String>>()
    // The key should use TypeLiteral and preserve the generic type parameter.
    val typeLiteral = key.typeLiteral
    assertThat(typeLiteral.type).isInstanceOf(ParameterizedType::class.java)
    val paramType = typeLiteral.type as ParameterizedType
    assertThat(paramType.rawType).isEqualTo(List::class.java)
    // Kotlin's List<String> maps to java.util.List<? extends String> due to declaration-site variance.
    assertThat(paramType.actualTypeArguments).hasSize(1)
  }

  @Test
  fun `keyOf with fully specified generic type is distinct from erased type`() {
    val genericKey = keyOf<List<String>>()
    val erasedKey = Key.get(List::class.java)
    // A TypeLiteral-based key with generics should differ from the erased class key.
    assertThat(genericKey).isNotEqualTo(erasedKey)
  }

  @Test
  fun `keyOf with annotation type qualifier`() {
    val key = keyOf<String>(TestAnnotation::class)
    assertThat(key.typeLiteral.rawType).isEqualTo(String::class.java)
    assertThat(key.annotation).isNull()
    assertThat(key.annotationType).isEqualTo(TestAnnotation::class.java)
  }

  @Test
  fun `keyOf with annotation instance qualifier`() {
    val named = Names.named("test")
    val key = keyOf<String>(named)
    assertThat(key.typeLiteral.rawType).isEqualTo(String::class.java)
    assertThat(key.annotation).isEqualTo(named)
  }

  @Test
  fun `keyOf with BindingQualifier TypeClassifier`() {
    val qualifier = TestAnnotation::class.qualifier
    val key = keyOf<String>(qualifier)
    assertThat(key.annotationType).isEqualTo(TestAnnotation::class.java)
  }

  @Test
  fun `keyOf with BindingQualifier InstanceQualifier`() {
    val named = Names.named("test")
    val qualifier = named.qualifier
    val key = keyOf<String>(qualifier)
    assertThat(key.annotation).isEqualTo(named)
  }

  @Test
  fun `keyOf with null qualifier creates unqualified key`() {
    val key = keyOf<String>(null as BindingQualifier?)
    assertThat(key).isEqualTo(Key.get(String::class.java))
    assertThat(key.annotation).isNull()
    assertThat(key.annotationType).isNull()
  }

  @Test
  fun `keyOf with generic type preserves type and can be used in Guice bindings`() {
    val key = keyOf<List<String>>()
    val injector =
      Guice.createInjector(
        object : KAbstractModule() {
          override fun configure() {
            bind(key).toInstance(listOf("hello", "world"))
          }
        }
      )
    val result = injector.getInstance(key)
    assertThat(result).containsExactly("hello", "world")
  }

  /**
   * This is the regression test for the bug fixed in this PR. When `keyOf` is called from a generic class with an
   * unresolved type variable (e.g., `keyOf<SomeType<T>>()` where T is a class type parameter, not reified), the
   * TypeLiteral captures an unresolved TypeVariable. Without the fix, Guice would throw `Key is not fully specified`
   * because it cannot handle TypeVariables.
   *
   * The fix detects unresolved type variables and falls back to the erased class, which Guice can handle.
   */
  @Test
  fun `keyOf with unresolved type variable falls back to erased class`() {
    // Simulate the pattern from BatchReceiverModule: a generic class calling keyOf<Wrapper<T>>()
    // where T is a class type parameter (not reified).
    val helper = GenericKeyCreator<String>()
    // This should not throw "Key is not fully specified".
    val key = helper.createKey()
    // The key should use the erased class (Wrapper) since the type variable can't be resolved.
    assertThat(key.typeLiteral.rawType).isEqualTo(Wrapper::class.java)
  }

  @Test
  fun `keyOf with unresolved type variable and annotation type qualifier`() {
    val helper = GenericKeyCreator<String>()
    val key = helper.createKeyWithAnnotationType()
    assertThat(key.typeLiteral.rawType).isEqualTo(Wrapper::class.java)
    assertThat(key.annotationType).isEqualTo(TestAnnotation::class.java)
  }

  @Test
  fun `keyOf with unresolved type variable and annotation instance qualifier`() {
    val helper = GenericKeyCreator<String>()
    val named = Names.named("test")
    val key = helper.createKeyWithAnnotationInstance(named)
    assertThat(key.typeLiteral.rawType).isEqualTo(Wrapper::class.java)
    assertThat(key.annotation).isEqualTo(named)
  }

  @Test
  fun `keyOf with unresolved type variable can be used in Guice bindings`() {
    val helper = GenericKeyCreator<String>()
    val key = helper.createKey()
    // The erased key can be bound and retrieved from an injector.
    val injector =
      Guice.createInjector(
        object : KAbstractModule() {
          override fun configure() {
            bind(key).toInstance(Wrapper("hello"))
          }
        }
      )
    @Suppress("UNCHECKED_CAST") val result = injector.getInstance(key) as Wrapper<String>
    assertThat(result.value).isEqualTo("hello")
  }

  @Test
  fun `containsTypeVariable returns false for simple class`() {
    assertThat(String::class.java.containsTypeVariable()).isFalse()
  }

  @Test
  fun `containsTypeVariable returns false for fully specified parameterized type`() {
    val type = object : TypeLiteral<List<String>>() {}.type
    assertThat(type.containsTypeVariable()).isFalse()
  }

  @Test
  fun `containsTypeVariable returns true for type variable`() {
    // Get a TypeVariable from a generic class's type parameters.
    val typeVar = Wrapper::class.java.typeParameters[0]
    assertThat(typeVar.containsTypeVariable()).isTrue()
  }

  @Test
  fun `containsTypeVariable returns true for parameterized type with type variable`() {
    // Get a field type like Wrapper<T> where T is unresolved.
    val wrapperField = GenericFieldHolder::class.java.getDeclaredField("field")
    val fieldType = wrapperField.genericType
    assertThat(fieldType.containsTypeVariable()).isTrue()
  }

  /** A generic wrapper class used to test keyOf with unresolved type variables. */
  class Wrapper<T>(val value: T)

  /**
   * Simulates the pattern from BatchReceiverModule where a generic class calls keyOf<SomeType<T>>() with an unresolved
   * class type parameter T.
   */
  class GenericKeyCreator<T : Any> {
    fun createKey(): Key<Wrapper<T>> = keyOf<Wrapper<T>>()

    fun createKeyWithAnnotationType(): Key<Wrapper<T>> = keyOf<Wrapper<T>>(TestAnnotation::class)

    fun createKeyWithAnnotationInstance(annotation: Annotation): Key<Wrapper<T>> = keyOf<Wrapper<T>>(annotation)
  }

  /** Helper class with a generic field for testing containsTypeVariable on field types. */
  class GenericFieldHolder<T> {
    @JvmField var field: Wrapper<T>? = null
  }
}

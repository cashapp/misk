package misk.web.extractors

import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.test.assertFailsWith
import misk.exceptions.BadRequestException
import misk.web.QueryParam
import misk.web.extractors.QueryParamFeatureBinding.Factory.toQueryBinding
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class QueryParamFeatureBindingTest {
  private val stringConverterFactories =
    listOf<StringConverter.Factory>(
      object : StringConverter.Factory {
        override fun create(kType: KType): StringConverter? {
          return if (kType.classifier == ValueClass::class) {
            StringConverter { ValueClass(it) }
          } else {
            null
          }
        }
      }
    )

  @Test
  fun simpleString() {
    val queryStringProcessor = TestMemberStore.stringParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("foo"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo("foo")
  }

  @Test
  fun simpleStringNotPresent() {
    assertFailsWith<BadRequestException> {
      val queryStringProcessor = TestMemberStore.stringParameter().toQueryBinding(stringConverterFactories)!!
      queryStringProcessor.parameterValue(listOf())
    }
  }

  @Test
  fun optionalStringPresent() {
    val queryStringProcessor = TestMemberStore.optionalStringParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("foo"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo("foo")
  }

  @Test
  fun optionalStringNotPresent() {
    val queryStringProcessor = TestMemberStore.optionalStringParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun stringList() {
    val queryStringProcessor = TestMemberStore.stringListParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("foo", "bar"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(listOf("foo", "bar"))
  }

  @Test
  fun stringListNotPresent() {
    assertFailsWith<BadRequestException> {
      val queryStringProcessor = TestMemberStore.stringListParameter().toQueryBinding(stringConverterFactories)!!
      queryStringProcessor.parameterValue(listOf())
    }
  }

  @Test
  fun optionalStringListPresent() {
    val queryStringProcessor = TestMemberStore.optionalStringListParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("foo", "bar"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(listOf("foo", "bar"))
  }

  @Test
  fun optionalStringListNotPresent() {
    val queryStringProcessor = TestMemberStore.optionalStringListParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun defaultStringPresent() {
    val queryStringProcessor = TestMemberStore.defaultStringParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("foo"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo("foo")
  }

  @Test
  fun defaultStringNotPresent() {
    val queryStringProcessor = TestMemberStore.defaultStringParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun defaultStringListPresent() {
    val queryStringProcessor = TestMemberStore.defaultStringListParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("foo", "bar"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(listOf("foo", "bar"))
  }

  @Test
  fun defaultStringListNotPresent() {
    val queryStringProcessor = TestMemberStore.defaultStringListParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun simpleInt() {
    val queryStringProcessor = TestMemberStore.intParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("42"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(42)
  }

  @Test
  fun simpleIntNotPresent() {
    assertFailsWith<BadRequestException> {
      val queryStringProcessor = TestMemberStore.intParameter().toQueryBinding(stringConverterFactories)!!
      queryStringProcessor.parameterValue(listOf())
    }
  }

  @Test
  fun invalidInt() {
    val queryStringProcessor = TestMemberStore.intParameter().toQueryBinding(stringConverterFactories)!!
    assertFailsWith<BadRequestException> { queryStringProcessor.parameterValue(listOf("forty two")) }
  }

  @Test
  fun optionalIntPresent() {
    val queryStringProcessor = TestMemberStore.optionalIntParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("42"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(42)
  }

  @Test
  fun optionalIntNotPresent() {
    val queryStringProcessor = TestMemberStore.optionalIntParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun defaultIntPresent() {
    val queryStringProcessor = TestMemberStore.defaultIntParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("43"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(43)
  }

  @Test
  fun defaultIntNotPresent() {
    val queryStringProcessor = TestMemberStore.defaultIntParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun intList() {
    val queryStringProcessor = TestMemberStore.intListParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("42", "23"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(listOf(42, 23))
  }

  @Test
  fun intListNotPresent() {
    assertFailsWith<BadRequestException> {
      val queryStringProcessor = TestMemberStore.intListParameter().toQueryBinding(stringConverterFactories)!!
      queryStringProcessor.parameterValue(listOf())
    }
  }

  @Test
  fun optionalIntListPresent() {
    val queryStringProcessor = TestMemberStore.optionalIntListParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("42", "23"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(listOf(42, 23))
  }

  @Test
  fun optionalIntListNotPresent() {
    val queryStringProcessor = TestMemberStore.optionalIntListParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun defaultIntListPresent() {
    val queryStringProcessor = TestMemberStore.defaultIntListParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("43", "24"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(listOf(43, 24))
  }

  @Test
  fun defaultIntListNotPresent() {
    val queryStringProcessor = TestMemberStore.defaultIntListParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun simpleLong() {
    val queryStringProcessor = TestMemberStore.longParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("42"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(42L)
  }

  @Test
  fun simpleEnum() {
    val queryStringProcessor = TestMemberStore.enumParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("ONE"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(TestEnum.ONE)
  }

  @Test
  fun optionalEnumPresent() {
    val queryStringProcessor = TestMemberStore.optionalEnumParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("ONE"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(TestEnum.ONE)
  }

  @Test
  fun optionalEnumNotPresent() {
    val queryStringProcessor = TestMemberStore.optionalEnumParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun defaultEnumPresent() {
    val queryStringProcessor = TestMemberStore.defaultEnumParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun unsupportedClass() {
    assertFailsWith<IllegalArgumentException> {
      TestMemberStore.unsupportedParameter().toQueryBinding(stringConverterFactories)
    }
  }

  @Test
  fun customStringConverterCanBeUsed() {
    val queryStringProcessor = TestMemberStore.valueClassParameter().toQueryBinding(stringConverterFactories)!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("foo"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(ValueClass("foo"))
  }

  enum class TestEnum {
    ONE,
    TWO,
  }

  @JvmInline value class ValueClass(val value: String)

  @Suppress("UNUSED_PARAMETER")
  internal class TestMemberStore {
    fun strTest(
      @QueryParam str: String,
      @QueryParam optStr: String?,
      @QueryParam listStr: List<String>,
      @QueryParam optListStr: List<String>?,
      @QueryParam defaultStr: String = "default",
      @QueryParam defaultListStr: List<String> = listOf("default1", "default2"),
    ) {}

    fun intTest(
      @QueryParam int: Int,
      @QueryParam optInt: Int?,
      @QueryParam listInt: List<Int>,
      @QueryParam optListInt: List<Int>?,
      @QueryParam defaultInt: Int = 42,
      @QueryParam defaultListInt: List<Int> = listOf(42, 23),
    ) {}

    fun longTest(@QueryParam long: Long) {}

    fun enumTest(
      @QueryParam anEnum: TestEnum,
      @QueryParam optEnum: TestEnum?,
      @QueryParam defaultEnum: TestEnum = TestEnum.ONE,
    ) {}

    fun unsupportedTest(@QueryParam hashMap: Map<String, String>) {}

    fun customStringConverterTest(@QueryParam valueClass: ValueClass) {}

    companion object {
      fun stringParameter(): KParameter = TestMemberStore::strTest.parameters.get(1)

      fun optionalStringParameter(): KParameter = TestMemberStore::strTest.parameters.get(2)

      fun stringListParameter(): KParameter = TestMemberStore::strTest.parameters.get(3)

      fun optionalStringListParameter(): KParameter = TestMemberStore::strTest.parameters.get(4)

      fun defaultStringParameter(): KParameter = TestMemberStore::strTest.parameters.get(5)

      fun defaultStringListParameter(): KParameter = TestMemberStore::strTest.parameters.get(6)

      fun intParameter(): KParameter = TestMemberStore::intTest.parameters.get(1)

      fun optionalIntParameter(): KParameter = TestMemberStore::intTest.parameters.get(2)

      fun intListParameter(): KParameter = TestMemberStore::intTest.parameters.get(3)

      fun optionalIntListParameter(): KParameter = TestMemberStore::intTest.parameters.get(4)

      fun defaultIntParameter(): KParameter = TestMemberStore::intTest.parameters.get(5)

      fun defaultIntListParameter(): KParameter = TestMemberStore::intTest.parameters.get(6)

      fun longParameter(): KParameter = TestMemberStore::longTest.parameters.get(1)

      fun enumParameter(): KParameter = TestMemberStore::enumTest.parameters.get(1)

      fun optionalEnumParameter(): KParameter = TestMemberStore::enumTest.parameters.get(2)

      fun defaultEnumParameter(): KParameter = TestMemberStore::enumTest.parameters.get(3)

      fun unsupportedParameter(): KParameter = TestMemberStore::unsupportedTest.parameters.get(1)

      fun valueClassParameter(): KParameter = TestMemberStore::customStringConverterTest.parameters.get(1)
    }
  }
}

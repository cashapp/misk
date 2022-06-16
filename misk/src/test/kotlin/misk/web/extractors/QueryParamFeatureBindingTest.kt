package misk.web.extractors

import misk.exceptions.BadRequestException
import misk.web.QueryParam
import misk.web.extractors.QueryParamFeatureBinding.Factory.toQueryBinding
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KParameter
import kotlin.test.assertFailsWith

internal class QueryParamFeatureBindingTest {
  @Test
  fun simpleString() {
    val queryStringProcessor = TestMemberStore.stringParameter().toQueryBinding()!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("foo"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo("foo")
  }

  @Test
  fun optionalStringPresent() {
    val queryStringProcessor = TestMemberStore.optionalStringParameter().toQueryBinding()!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("foo"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo("foo")
  }

  @Test
  fun optionalStringNotPresent() {
    val queryStringProcessor = TestMemberStore.optionalStringParameter().toQueryBinding()!!
    val extractedResult = queryStringProcessor.parameterValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun stringList() {
    val queryStringProcessor = TestMemberStore.stringListParameter().toQueryBinding()!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("foo", "bar"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(listOf("foo", "bar"))
  }

  @Test
  fun optionalStringListPresent() {
    val queryStringProcessor = TestMemberStore.optionalStringListParameter().toQueryBinding()!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("foo", "bar"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(listOf("foo", "bar"))
  }

  @Test
  fun optionalStringListNotPresent() {
    val queryStringProcessor = TestMemberStore.optionalStringListParameter().toQueryBinding()!!
    val extractedResult = queryStringProcessor.parameterValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun simpleInt() {
    val queryStringProcessor = TestMemberStore.intParameter().toQueryBinding()!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("42"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(42)
  }

  @Test
  fun invalidInt() {
    val queryStringProcessor = TestMemberStore.intParameter().toQueryBinding()!!
    assertFailsWith<BadRequestException> {
      queryStringProcessor.parameterValue(listOf("forty two"))
    }
  }

  @Test
  fun optionalIntPresent() {
    val queryStringProcessor = TestMemberStore.optionalIntParameter().toQueryBinding()!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("42"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(42)
  }

  @Test
  fun optionalIntNotPresent() {
    val queryStringProcessor = TestMemberStore.optionalIntParameter().toQueryBinding()!!
    val extractedResult = queryStringProcessor.parameterValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun intList() {
    val queryStringProcessor = TestMemberStore.intListParameter().toQueryBinding()!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("42", "23"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(listOf(42, 23))
  }

  @Test
  fun optionalIntListPresent() {
    val queryStringProcessor = TestMemberStore.optionalIntListParameter().toQueryBinding()!!
    val extractedResult = queryStringProcessor.parameterValue(
      listOf("42", "23")
    )
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(listOf(42, 23))
  }

  @Test
  fun optionalIntListNotPresent() {
    val queryStringProcessor = TestMemberStore.optionalIntListParameter().toQueryBinding()!!
    val extractedResult = queryStringProcessor.parameterValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun simpleLong() {
    val queryStringProcessor = TestMemberStore.longParameter().toQueryBinding()!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("42"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(42L)
  }

  @Test
  fun simpleEnum() {
    val queryStringProcessor = TestMemberStore.enumParameter().toQueryBinding()!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("ONE"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(TestEnum.ONE)
  }

  @Test
  fun optionalEnumPresent() {
    val queryStringProcessor = TestMemberStore.optionalEnumParameter().toQueryBinding()!!
    val extractedResult = queryStringProcessor.parameterValue(listOf("ONE"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(TestEnum.ONE)
  }

  @Test
  fun optionalEnumNotPresent() {
    val queryStringProcessor = TestMemberStore.optionalEnumParameter().toQueryBinding()!!
    val extractedResult = queryStringProcessor.parameterValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun defaultEnumPresent() {
    val queryStringProcessor = TestMemberStore.defaultEnumParameter().toQueryBinding()!!
    val extractedResult = queryStringProcessor.parameterValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun unsupportedClass() {
    assertFailsWith<IllegalArgumentException> {
      TestMemberStore.unsupportedParameter().toQueryBinding()
    }
  }

  enum class TestEnum {
    ONE,
    TWO
  }

  @Suppress("UNUSED_PARAMETER")
  internal class TestMemberStore {
    fun strTest(
      @QueryParam str: String,
      @QueryParam optStr: String?,
      @QueryParam listStr: List<String>,
      @QueryParam optListStr: List<String>?
    ) {
    }

    fun intTest(
      @QueryParam int: Int,
      @QueryParam optInt: Int?,
      @QueryParam listInt: List<Int>,
      @QueryParam optListInt: List<Int>?
    ) {
    }

    fun longTest(
      @QueryParam long: Long
    ) {
    }

    fun enumTest(
      @QueryParam anEnum: TestEnum,
      @QueryParam optEnum: TestEnum?,
      @QueryParam defaultEnum: TestEnum = TestEnum.ONE
    ) {
    }

    fun unsupportedTest(
      @QueryParam hashMap: Map<String, String>
    ) {
    }

    companion object {
      fun stringParameter(): KParameter = TestMemberStore::strTest.parameters.get(1)
      fun optionalStringParameter(): KParameter = TestMemberStore::strTest.parameters.get(2)
      fun stringListParameter(): KParameter = TestMemberStore::strTest.parameters.get(3)
      fun optionalStringListParameter(): KParameter = TestMemberStore::strTest.parameters.get(4)
      fun intParameter(): KParameter = TestMemberStore::intTest.parameters.get(1)
      fun optionalIntParameter(): KParameter = TestMemberStore::intTest.parameters.get(2)
      fun intListParameter(): KParameter = TestMemberStore::intTest.parameters.get(3)
      fun optionalIntListParameter(): KParameter = TestMemberStore::intTest.parameters.get(4)
      fun longParameter(): KParameter = TestMemberStore::longTest.parameters.get(1)
      fun enumParameter(): KParameter = TestMemberStore::enumTest.parameters.get(1)
      fun optionalEnumParameter(): KParameter = TestMemberStore::enumTest.parameters.get(2)
      fun defaultEnumParameter(): KParameter = TestMemberStore::enumTest.parameters.get(3)
      fun unsupportedParameter(): KParameter = TestMemberStore::unsupportedTest.parameters.get(1)
    }
  }
}

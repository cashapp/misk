package misk.web.extractors

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KParameter

internal class QueryStringParameterProcessorTest {
  @Test
  fun simpleString() {
    val queryStringProcessor = QueryStringParameterProcessor(TestMemberStore.stringParameter())
    val extractedResult = queryStringProcessor.extractFunctionArgumentValue(listOf("foo"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo("foo")
  }

  @Test
  fun optionalStringPresent() {
    val queryStringProcessor =
        QueryStringParameterProcessor(TestMemberStore.optionalStringParameter())
    val extractedResult = queryStringProcessor.extractFunctionArgumentValue(listOf("foo"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo("foo")
  }

  @Test
  fun optionalStringNotPresent() {
    val queryStringProcessor =
        QueryStringParameterProcessor(TestMemberStore.optionalStringParameter())
    val extractedResult = queryStringProcessor.extractFunctionArgumentValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun stringList() {
    val queryStringProcessor = QueryStringParameterProcessor(
        TestMemberStore.stringListParameter()
    )
    val extractedResult = queryStringProcessor.extractFunctionArgumentValue(
        listOf("foo", "bar")
    )
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(listOf("foo", "bar"))
  }

  @Test
  fun optionalStringListPresent() {
    val queryStringProcessor = QueryStringParameterProcessor(
        TestMemberStore.optionalStringListParameter()
    )
    val extractedResult = queryStringProcessor.extractFunctionArgumentValue(
        listOf("foo", "bar")
    )
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(listOf("foo", "bar"))
  }

  @Test
  fun optionalStringListNotPresent() {
    val queryStringProcessor = QueryStringParameterProcessor(
        TestMemberStore.optionalStringListParameter()
    )
    val extractedResult = queryStringProcessor.extractFunctionArgumentValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun simpleInt() {
    val queryStringProcessor = QueryStringParameterProcessor(TestMemberStore.intParameter())
    val extractedResult = queryStringProcessor.extractFunctionArgumentValue(listOf("42"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(42)
  }

  @Test
  fun invalidInt() {
    val queryStringProcessor = QueryStringParameterProcessor(TestMemberStore.intParameter())
    try {
      queryStringProcessor.extractFunctionArgumentValue(listOf("forty two"))
      assert(false)
    } catch (e: IllegalArgumentException) {
      // expected
    }
  }

  @Test
  fun optionalIntPresent() {
    val queryStringProcessor = QueryStringParameterProcessor(TestMemberStore.optionalIntParameter())
    val extractedResult = queryStringProcessor.extractFunctionArgumentValue(listOf("42"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(42)
  }

  @Test
  fun optionalIntNotPresent() {
    val queryStringProcessor = QueryStringParameterProcessor(TestMemberStore.optionalIntParameter())
    val extractedResult = queryStringProcessor.extractFunctionArgumentValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun intList() {
    val queryStringProcessor = QueryStringParameterProcessor(TestMemberStore.intListParameter())
    val extractedResult = queryStringProcessor.extractFunctionArgumentValue(listOf("42", "23"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(listOf(42, 23))
  }

  @Test
  fun optionalIntListPresent() {
    val queryStringProcessor = QueryStringParameterProcessor(
        TestMemberStore.optionalIntListParameter()
    )
    val extractedResult = queryStringProcessor.extractFunctionArgumentValue(
        listOf("42", "23")
    )
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(listOf(42, 23))
  }

  @Test
  fun optionalIntListNotPresent() {
    val queryStringProcessor = QueryStringParameterProcessor(
        TestMemberStore.optionalIntListParameter()
    )
    val extractedResult = queryStringProcessor.extractFunctionArgumentValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun simpleLong() {
    val queryStringProcessor = QueryStringParameterProcessor(TestMemberStore.longParameter())
    val extractedResult = queryStringProcessor.extractFunctionArgumentValue(listOf("42"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(42L)
  }

  @Test
  fun simpleEnum() {
    val queryStringProcessor = QueryStringParameterProcessor(TestMemberStore.enumParameter())
    val extractedResult = queryStringProcessor.extractFunctionArgumentValue(listOf("ONE"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(TestEnum.ONE)
  }

  @Test
  fun optionalEnumPresent() {
    val queryStringProcessor = QueryStringParameterProcessor(
        TestMemberStore.optionalEnumParameter()
    )
    val extractedResult = queryStringProcessor.extractFunctionArgumentValue(listOf("ONE"))
    assertThat(extractedResult).isNotNull()
    assertThat(extractedResult).isEqualTo(TestEnum.ONE)
  }

  @Test
  fun optionalEnumNotPresent() {
    val queryStringProcessor = QueryStringParameterProcessor(
        TestMemberStore.optionalEnumParameter()
    )
    val extractedResult = queryStringProcessor.extractFunctionArgumentValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun defaultEnumPresent() {
    val queryStringProcessor = QueryStringParameterProcessor(
        TestMemberStore.defaultEnumParameter()
    )
    val extractedResult = queryStringProcessor.extractFunctionArgumentValue(listOf())
    assertThat(extractedResult).isNull()
  }

  @Test
  fun unsupportedClass() {
    try {
      QueryStringParameterProcessor(TestMemberStore.unsupportedParameter())
      assert(false)
    } catch (e: IllegalArgumentException) {
      // should be thrown
    }
  }

  enum class TestEnum {
    ONE,
    TWO
  }

  @Suppress("UNUSED_PARAMETER")
  internal class TestMemberStore {
    fun strTest(
        str: String,
        optStr: String?,
        listStr: List<String>,
        optListStr: List<String>?
    ) {
    }

    fun intTest(
        int: Int,
        optInt: Int?,
        listInt: List<Int>,
        optListInt: List<Int>?
    ) {
    }

    fun longTest(long: Long) {
    }

    fun enumTest(
        anEnum: TestEnum,
        optEnum: TestEnum?,
        defaultEnum: TestEnum = TestEnum.ONE
    ) {
    }

    fun unsupportedTest(hashMap: Map<String, String>) {
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

package wisp.feature.testing

import wisp.feature.Attributes
import wisp.feature.BooleanFeatureFlag
import wisp.feature.DoubleFeatureFlag
import wisp.feature.EnumFeatureFlag
import wisp.feature.Feature
import wisp.feature.IntFeatureFlag
import wisp.feature.JsonFeatureFlag
import wisp.feature.StringFeatureFlag

sealed interface TestFlag<T : Any> {
  val username: String
  val segment: String
}

data class TestBooleanFlag(
  override val username: String = "test-boolean-username",
  override val segment: String = "test-boolean-segment"
) : BooleanFeatureFlag, TestFlag<Boolean> {
  override val feature = Feature("test-boolean-flag")
  override val key = username
  override val attributes = Attributes().with("segment", segment)
}

data class TestStringFlag(
  override val username: String = "test-string-username",
  override val segment: String = "test-string-segment"
): StringFeatureFlag, TestFlag<String> {
  override val feature = Feature("test-string-flag")
  override val key = username
  override val attributes = Attributes().with("segment", segment)
}

data class TestIntFlag(
  override val username: String = "test-int-username",
  override val segment: String = "test-int-segment"
): IntFeatureFlag, TestFlag<Int> {
  override val feature = Feature("test-int-flag")
  override val key = username
  override val attributes = Attributes().with("segment", segment)
}

data class TestDoubleFlag(
  override val username: String = "test-double-username",
  override val segment: String = "test-double-segment"
): DoubleFeatureFlag, TestFlag<Double> {
  override val feature = Feature("test-double-flag")
  override val key = username
  override val attributes = Attributes().with("segment", segment)
}

enum class TestEnum { TEST_VALUE_1, TEST_VALUE_2 }
data class TestEnumFlag(
  override val username: String = "test-enum-username",
  override val segment: String = "test-enum-segment"
): EnumFeatureFlag<TestEnum>, TestFlag<TestEnum> {
  override val feature = Feature("test-enum-flag")
  override val key = username
  override val attributes = Attributes().with("segment", segment)
  override val returnType = TestEnum::class.java
}


data class TestJsonObject(val field1: String, val field2: Int)
data class TestJsonFlag(
  override val username: String = "test-json-username",
  override val segment: String = "test-json-segment"
) : JsonFeatureFlag<TestJsonObject>, TestFlag<TestJsonObject> {
  override val feature = Feature("test-json-featurew")
  override val key = username
  override val attributes = Attributes().with("segment", segment)
  override val returnType = TestJsonObject::class.java
}

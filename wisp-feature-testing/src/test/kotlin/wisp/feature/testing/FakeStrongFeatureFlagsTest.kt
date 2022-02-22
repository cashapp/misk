package wisp.feature.testing

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import wisp.feature.Attributes
import wisp.feature.BooleanFeatureFlag
import wisp.feature.Feature

internal class FakeStrongFeatureFlagsTest {
  data class TestBooleanFlag(
    val username: String,
    val segment: String
  ): BooleanFeatureFlag {
    override val feature = Feature("test-boolean-flag")
    override val key = username
    override val attributes = Attributes().with("segment", segment)
  }
}

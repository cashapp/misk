package misk.feature.testing

import misk.feature.Feature
import misk.feature.getEnum
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class TestFeatureFlagsTest {
  val FEATURE = Feature("foo")
  val OTHER_FEATURE = Feature("bar")
  val TOKEN = "cust_abcdef123"

  val subject = TestFeatureFlags()

  @Test
  fun getInt() {
    // Default throws.
    assertThrows<RuntimeException> {
      subject.getInt(FEATURE, TOKEN)
    }

    // Can be overridden
    subject.override(FEATURE, 3)
    subject.override(OTHER_FEATURE, 5)
    assertThat(subject.getInt(FEATURE, TOKEN)).isEqualTo(3)
    assertThat(subject.getInt(OTHER_FEATURE, TOKEN)).isEqualTo(5)
  }

  @Test
  fun getEnum() {
    // Defaults to first enum.
    assertThat(subject.getEnum<Dinosaur>(FEATURE, TOKEN))
        .isEqualTo(Dinosaur.PTERODACTYL)

    // Can be overridden
    subject.override(FEATURE, Dinosaur.TYRANNOSAURUS)
    assertThat(subject.getEnum<Dinosaur>(FEATURE, TOKEN))
        .isEqualTo(Dinosaur.TYRANNOSAURUS)

    subject.reset()
    assertThat(subject.getEnum<Dinosaur>(FEATURE, TOKEN))
        .isEqualTo(Dinosaur.PTERODACTYL)
  }

  enum class Dinosaur {
    PTERODACTYL,
    TYRANNOSAURUS
  }
}
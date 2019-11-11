package misk.feature.testing

import misk.feature.Feature
import misk.feature.getEnum
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalArgumentException

internal class FakeFeatureFlagsTest {
  val FEATURE = Feature("foo")
  val OTHER_FEATURE = Feature("bar")
  val TOKEN = "cust_abcdef123"

  val subject = FakeFeatureFlags()

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

    // Can override with specific keys
    subject.overrideKey(FEATURE, "joker", 42)
    assertThat(subject.getInt(FEATURE, TOKEN)).isEqualTo(3)
    assertThat(subject.getInt(FEATURE, "joker")).isEqualTo(42)
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

    // Can override with specific keys
    subject.overrideKey(FEATURE, "joker", Dinosaur.PTERODACTYL)
    assertThat(subject.getEnum<Dinosaur>(FEATURE, TOKEN))
        .isEqualTo(Dinosaur.TYRANNOSAURUS)
    assertThat(subject.getEnum<Dinosaur>(FEATURE, "joker"))
        .isEqualTo(Dinosaur.PTERODACTYL)

    subject.reset()
    assertThat(subject.getEnum<Dinosaur>(FEATURE, TOKEN))
        .isEqualTo(Dinosaur.PTERODACTYL)
  }

  @Test
  fun invalidKeys() {
    assertThrows<IllegalArgumentException> {
      subject.getString(Feature("which-dinosaur"), "bad(token)")
    }
    assertThrows<IllegalArgumentException> {
      subject.getString(Feature("which-dinosaur"), "Bearer auth-token")
    }
    assertThrows<IllegalArgumentException> {
      subject.getEnum<Dinosaur>(Feature("which-dinosaur"), "")
    }
  }

  enum class Dinosaur {
    PTERODACTYL,
    TYRANNOSAURUS
  }
}
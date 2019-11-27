package misk.feature.testing

import misk.feature.Feature
import misk.feature.getEnum
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
    subject.override(FEATURE, 3)
    assertThrows<IllegalArgumentException> {
      subject.getInt(FEATURE, "bad(token)")
    }
    assertThrows<IllegalArgumentException> {
      subject.getInt(FEATURE, "Bearer auth-token")
    }
    assertThrows<IllegalArgumentException> {
      subject.getInt(FEATURE, "")
    }
  }

  @Test
  fun validKeys() {
    subject.override(FEATURE, 3)
    subject.getInt(FEATURE, "hello")
    subject.getInt(FEATURE, "09afAF") // hex.
    subject.getInt(FEATURE, "AZ27=") // base32.
    subject.getInt(FEATURE, "azAZ09+/=") // base64.
    subject.getInt(FEATURE, "azAZ09-_=") // base64url.
    subject.getInt(FEATURE, "azAZ09-_.~$") // unreserved URL characters.
  }

  enum class Dinosaur {
    PTERODACTYL,
    TYRANNOSAURUS
  }
}

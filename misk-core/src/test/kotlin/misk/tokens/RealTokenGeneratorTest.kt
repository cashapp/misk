package misk.tokens

import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest
class RealTokenGeneratorTest {
  @MiskTestModule
  val module = Modules.override(MiskTestingServiceModule()).with(TokenGeneratorModule())

  @Inject lateinit var tokenGenerator: TokenGenerator

  @Test
  fun happyPath() {
    val token0 = tokenGenerator.generate()
    val token1 = tokenGenerator.generate()
    assertThat(token0).matches("[${TokenGenerator.alphabet}]{25}")
    assertThat(token1).matches("[${TokenGenerator.alphabet}]{25}")
    assertThat(token0).isNotEqualTo(token1)
  }

  @Test
  fun labelsAreIgnored() {
    assertThat(tokenGenerator.generate("payment")).doesNotContain("payment")
  }

  @Test
  fun customLength() {
    assertThat(tokenGenerator.generate(length = 4)).matches("[${TokenGenerator.alphabet}]{4}")
    assertThat(tokenGenerator.generate(length = 12)).matches("[${TokenGenerator.alphabet}]{12}")
    assertThat(tokenGenerator.generate(length = 25)).matches("[${TokenGenerator.alphabet}]{25}")
  }

  @Test
  fun lengthOutOfBounds() {
    assertFailsWith<IllegalArgumentException> {
      tokenGenerator.generate(length = 3)
    }
    assertFailsWith<IllegalArgumentException> {
      tokenGenerator.generate(length = 26)
    }
  }

  @Test
  fun canonicalize() {
    assertThat(TokenGenerator.canonicalize("iIlLoO")).isEqualTo("111100")
    assertThat(TokenGenerator.canonicalize("Pterodactyl")).isEqualTo("pter0dacty1")
    assertThat(TokenGenerator.canonicalize("Veloci Raptor")).isEqualTo("ve10c1rapt0r")
  }

  @Test
  fun canonicalizeUnexpectedCharacters() {
    assertFailsWith<IllegalArgumentException> {
      TokenGenerator.canonicalize("Dinosaur") // u.
    }
    assertFailsWith<IllegalArgumentException> {
      TokenGenerator.canonicalize("Veloci_Raptor") // _.
    }
    assertFailsWith<IllegalArgumentException> {
      TokenGenerator.canonicalize("Velociräptor") // ä.
    }
  }

  @Test
  fun canonicalizeUnexpectedLength() {
    assertFailsWith<IllegalArgumentException> {
      TokenGenerator.canonicalize("a b c") // 3 characters after stripping spaces.
    }
    assertFailsWith<IllegalArgumentException> {
      TokenGenerator.canonicalize("12345678901234567890123456") // 26 characters.
    }
  }
}

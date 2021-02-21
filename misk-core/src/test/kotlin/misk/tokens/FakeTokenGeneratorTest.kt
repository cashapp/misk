package misk.tokens

import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.concurrent.thread
import kotlin.test.assertFailsWith

@MiskTest
class FakeTokenGeneratorTest {
  @MiskTestModule
  val module = FakeTokenGeneratorModule()

  @Inject lateinit var tokenGenerator: TokenGenerator

  @Test
  fun happyPath() {
    val token0 = tokenGenerator.generate()
    val token1 = tokenGenerator.generate()
    assertThat(token0).isEqualTo("0000000000000000000000001")
    assertThat(token1).isEqualTo("0000000000000000000000002")
  }

  @Test
  fun labelsArePrefixes() {
    val token0 = tokenGenerator.generate("payment")
    val token1 = tokenGenerator.generate("payment")
    assertThat(token0).isEqualTo("payment000000000000000001")
    assertThat(token1).isEqualTo("payment000000000000000002")
  }

  @Test
  fun labelsAreCanonicalized() {
    val token0 = tokenGenerator.generate("customer")
    val token1 = tokenGenerator.generate("customer")
    assertThat(token0).isEqualTo("cst0mer000000000000000001")
    assertThat(token1).isEqualTo("cst0mer000000000000000002")
  }

  @Test
  fun labelsAreNamespaces() {
    val token0 = tokenGenerator.generate("customer")
    val token1 = tokenGenerator.generate("payment")
    val token2 = tokenGenerator.generate("customer")
    val token3 = tokenGenerator.generate("payment")
    assertThat(token0).isEqualTo("cst0mer000000000000000001")
    assertThat(token1).isEqualTo("payment000000000000000001")
    assertThat(token2).isEqualTo("cst0mer000000000000000002")
    assertThat(token3).isEqualTo("payment000000000000000002")
  }

  @Test
  fun generationIsThreadSafe() {
    (1..30).map {
      thread { (1..100).forEach { tokenGenerator.generate() } }
    }.forEach { it.join() }
    assertThat(tokenGenerator.generate()).isEqualTo("0000000000000000000003001")
  }

  @Test
  fun customLength() {
    assertThat(tokenGenerator.generate(label = "payment", length = 4))
      .isEqualTo("pay1")
    assertThat(tokenGenerator.generate(label = "payment", length = 7))
      .isEqualTo("paymen2")
    assertThat(tokenGenerator.generate(label = "payment", length = 8))
      .isEqualTo("payment3")
    assertThat(tokenGenerator.generate(label = "payment", length = 9))
      .isEqualTo("payment04")
    assertThat(tokenGenerator.generate(label = "payment", length = 12))
      .isEqualTo("payment00005")
    assertThat(tokenGenerator.generate(label = "payment", length = 25))
      .isEqualTo("payment000000000000000006")
  }

  @Test
  fun customLengthWithLargeSuffix() {
    // Fast-forward the next token suffix.
    (tokenGenerator as FakeTokenGenerator).nextByLabel.put("payment", AtomicLong(12345L))

    assertThat(tokenGenerator.generate(label = "payment", length = 4))
      .isEqualTo("2345")
    assertThat(tokenGenerator.generate(label = "payment", length = 7))
      .isEqualTo("pa12346")
    assertThat(tokenGenerator.generate(label = "payment", length = 8))
      .isEqualTo("pay12347")
    assertThat(tokenGenerator.generate(label = "payment", length = 9))
      .isEqualTo("paym12348")
    assertThat(tokenGenerator.generate(label = "payment", length = 12))
      .isEqualTo("payment12349")
    assertThat(tokenGenerator.generate(label = "payment", length = 25))
      .isEqualTo("payment000000000000012350")
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
}

package wisp.token

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

class FakeTokenGeneratorTest : FreeSpec({
  lateinit var tokenGenerator: TokenGenerator

  beforeTest {
    tokenGenerator = FakeTokenGenerator()
  }

  "Happy Path" {
    val token0 = tokenGenerator.generate()
    val token1 = tokenGenerator.generate()
    token0 shouldBe "0000000000000000000000001"
    token1 shouldBe "0000000000000000000000002"
  }

  "Labels Are Prefixes" {
    val token0 = tokenGenerator.generate("payment")
    val token1 = tokenGenerator.generate("payment")
    token0 shouldBe "payment000000000000000001"
    token1 shouldBe "payment000000000000000002"
  }

  "Labels Are Canonicalized" {
    val token0 = tokenGenerator.generate("customer")
    val token1 = tokenGenerator.generate("customer")
    token0 shouldBe "cst0mer000000000000000001"
    token1 shouldBe "cst0mer000000000000000002"
  }

  "Labels Are Namespaces" {
    val token0 = tokenGenerator.generate("customer")
    val token1 = tokenGenerator.generate("payment")
    val token2 = tokenGenerator.generate("customer")
    val token3 = tokenGenerator.generate("payment")
    token0 shouldBe "cst0mer000000000000000001"
    token1 shouldBe "payment000000000000000001"
    token2 shouldBe "cst0mer000000000000000002"
    token3 shouldBe "payment000000000000000002"
  }

  "Generation Is Thread Safe" {
    (1..30).map {
      thread { (1..100).forEach { _ -> tokenGenerator.generate() } }
    }.forEach { it.join() }
    tokenGenerator.generate() shouldBe "0000000000000000000003001"
  }

  "Custom Length" {
    tokenGenerator.generate(label = "payment", length = 4) shouldBe "pay1"
    tokenGenerator.generate(label = "payment", length = 7) shouldBe "paymen2"
    tokenGenerator.generate(label = "payment", length = 8) shouldBe "payment3"
    tokenGenerator.generate(label = "payment", length = 9) shouldBe "payment04"
    tokenGenerator.generate(label = "payment", length = 12) shouldBe "payment00005"
    tokenGenerator.generate(label = "payment", length = 25) shouldBe "payment000000000000000006"
  }

  "Custom Length With Large Suffix" {
    // Fast-forward the next token suffix.
    (tokenGenerator as FakeTokenGenerator).nextByLabel["payment"] = AtomicLong(12345L)

    tokenGenerator.generate(label = "payment", length = 4) shouldBe "2345"
    tokenGenerator.generate(label = "payment", length = 7) shouldBe "pa12346"
    tokenGenerator.generate(label = "payment", length = 8) shouldBe "pay12347"
    tokenGenerator.generate(label = "payment", length = 9) shouldBe "paym12348"
    tokenGenerator.generate(label = "payment", length = 12) shouldBe "payment12349"
    tokenGenerator.generate(label = "payment", length = 25) shouldBe "payment000000000000012350"
  }

  "Length Out Of Bounds" {
    shouldThrow<IllegalArgumentException> {
      tokenGenerator.generate(length = 3)
    }
    shouldThrow<IllegalArgumentException> {
      tokenGenerator.generate(length = 26)
    }
  }
})

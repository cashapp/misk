package misk.tokens

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch

class RealTokenGeneratorTest :
  FreeSpec({
    val tokenGenerator: TokenGenerator2 = RealTokenGenerator2()

    "Happy Path" {
      val token0 = tokenGenerator.generate()
      val token1 = tokenGenerator.generate()

      token0 shouldMatch "[${TokenGenerator2.alphabet}]{25}"
      token1 shouldMatch "[${TokenGenerator2.alphabet}]{25}"
      token0 shouldNotBe token1
    }

    "Labels Are Ignored" { tokenGenerator.generate("payment").contains("payment") shouldNotBe true }

    "Custom Length" {
      tokenGenerator.generate(length = 4) shouldMatch "[${TokenGenerator2.alphabet}]{4}"
      tokenGenerator.generate(length = 12) shouldMatch "[${TokenGenerator2.alphabet}]{12}"
      tokenGenerator.generate(length = 25) shouldMatch "[${TokenGenerator2.alphabet}]{25}"
    }

    "Length Out Of Bounds" {
      shouldThrow<IllegalArgumentException> { tokenGenerator.generate(length = 3) }
      shouldThrow<IllegalArgumentException> { tokenGenerator.generate(length = 26) }
    }

    "Canonicalize" {
      TokenGenerator2.canonicalize("iIlLoO") shouldBe "111100"
      TokenGenerator2.canonicalize("Pterodactyl") shouldBe "pter0dacty1"
      TokenGenerator2.canonicalize("Veloci Raptor") shouldBe "ve10c1rapt0r"
    }

    "Canonicalize Unexpected Characters" {
      shouldThrow<IllegalArgumentException> {
        TokenGenerator2.canonicalize("Dinosaur") // u.
      }
      shouldThrow<IllegalArgumentException> {
        TokenGenerator2.canonicalize("Veloci_Raptor") // _.
      }
      shouldThrow<IllegalArgumentException> {
        TokenGenerator2.canonicalize("Velociräptor") // ä.
      }
    }

    "Canonicalize Unexpected Length" {
      shouldThrow<IllegalArgumentException> {
        TokenGenerator2.canonicalize("a b c") // 3 characters after stripping spaces.
      }
      shouldThrow<IllegalArgumentException> {
        TokenGenerator2.canonicalize("12345678901234567890123456") // 26 characters.
      }
    }
  })

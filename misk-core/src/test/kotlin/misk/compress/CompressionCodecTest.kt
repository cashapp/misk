package com.squareup.misk.compress

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

abstract class CompressionCodecTest {
  abstract val codecUnderTest: CompressionCodec

  private val fixtureValues = listOf(
      "perfectly splendid",
      "346af795eba789033dd4696b18eff03d",
      "0f6a64771ec115039851676c6541d24b8758684a803ae9aff45a0bd6cf5f7039",
      "9eee9f73ae587affa120bcc89a998a0e10d29581762177b5ac93f560ae829a48efc9fdb01a2f60a956ce6f2ec64a66d1db79abd38d85c39b07652c9f5e9459b3",
      "repetition repetition repetition repetition",
      "mostly distinct letters"
  ).map { it.toByteArray() }

  @TestFactory
  fun `test reversibility of small input`(): List<DynamicTest> {
    return fixtureValues.mapIndexed { index, testFixture ->
      DynamicTest.dynamicTest("test fixture $index") {
        val encoded = codecUnderTest.encode(testFixture)
        assertThat(codecUnderTest.decode(encoded)).isEqualTo(testFixture)
      }
    }
  }
}
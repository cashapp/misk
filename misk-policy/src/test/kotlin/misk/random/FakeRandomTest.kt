package misk.random

import com.google.inject.Module
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.inject.Inject

@MiskTest(startService = false)
internal class FakeRandomTest {
  @MiskTestModule val module: Module = object : KAbstractModule() {
  }

  @Inject lateinit var fakeRandom: FakeRandom

  @Nested
  inner class BoundedNextInt {
    @Test
    fun `will not return outside bound`() {
      fakeRandom.nextInt = 99
      assertThat(fakeRandom.nextInt(10)).isEqualTo(9)
    }

    @Test
    fun `cannot return bound`() {
      fakeRandom.nextInt = 10
      assertThat(fakeRandom.nextInt(10)).isEqualTo(0)
    }

    @Test
    fun `can be told to return 0`() {
      fakeRandom.nextInt = 0
      assertThat(fakeRandom.nextInt(10)).isEqualTo(0)
    }

    @Test
    fun `bound of 0 is invalid`() {
      fakeRandom.nextInt = 0
      assertThrows<IllegalArgumentException> {
        fakeRandom.nextInt(0)
      }
    }

    @Test
    fun `bound of -1 is invalid`() {
      fakeRandom.nextInt = 0
      assertThrows<IllegalArgumentException> {
        fakeRandom.nextInt(-1)
      }
    }

    @Test
    fun `nextInt of -1 is invalid`() {
      fakeRandom.nextInt = -1
      assertThrows<IllegalArgumentException> {
        fakeRandom.nextInt(1)
      }
    }
  }
}

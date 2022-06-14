package misk.testing

import com.google.inject.testing.fieldbinder.Bind
import misk.mockito.Mockito.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mock
import javax.inject.Inject

@MiskTest
class MockFieldTest {

  interface CountService {
    fun getAndInc(): Int
  }

  class Service @Inject constructor(val countService: CountService) {
    fun getName(): String {
      return "Name ${countService.getAndInc()}"
    }
  }

  @Inject lateinit var service: Service

  @Nested
  inner class `Bind Using mock annotation` {
    //
    @Mock @Bind lateinit var counterService: CountService

    @Test
    fun test() {
      whenever(counterService.getAndInc()).thenReturn(1313)
      assertThat(service.getName()).isEqualTo("Name 1313")
    }
  }
  @Nested
  inner class `Bind using fakes` {
    @Bind val counterService = object : CountService {
      var counter = 0
      override fun getAndInc(): Int {
        return counter++
      }
    }

    @Test
    fun test() {
      whenever(counterService.getAndInc()).thenReturn(1313)
      assertThat(service.getName()).isEqualTo("Name 1")
    }
  }
}

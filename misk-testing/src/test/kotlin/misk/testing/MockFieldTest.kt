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
  @Mock @Bind lateinit var counterService: CountService

  @Test
  fun test() {
    whenever(counterService.getAndInc()).thenReturn(1313)
    assertThat(service.getName()).isEqualTo("Name 1313")
  }

  interface HelloService {
    fun greeting(): String
  }

  @Nested
  inner class nestedTest {
    @Mock @Bind lateinit var helloService: HelloService
    // This was already bind by the parent class. So I'm not sure why someone would want to inject
    // it on the inner clas, but I wanted to check if this would work.
    @Inject lateinit var counterService2: CountService

    @Test
    fun testNested() {
      assertThat(helloService).isNotNull()
      assertThat(counterService).isNotNull()
      assertThat(counterService2).isNotNull()
    }

  }
}

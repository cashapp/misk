package misk.testing.fieldbind

import com.google.inject.testing.fieldbinder.Bind
import jakarta.inject.Inject
import misk.testing.MiskTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@MiskTest
class BindFieldUsingMockTest {
  @Inject lateinit var ticketDispenser: TicketDispenserService
  @Bind val counterService: CountService = mock()

  @Test
  fun `bind CounterService using mockito`() {
    whenever(counterService.getAndInc()).thenReturn(42)
    assertThat(ticketDispenser.getTicket()).isEqualTo("Your Number is 42")
  }
}

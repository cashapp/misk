package misk.testing.fieldbind

import com.google.inject.testing.fieldbinder.Bind
import misk.testing.MiskTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class BindFieldUsingFakeTest {
  @Inject lateinit var ticketDispenser: TicketDispenserService
  @Bind(to = CountService::class) val counterService = FakeCountService()

  @Test
  fun `bind CounterService using fakes`() {
    counterService.count = 5
    assertThat(ticketDispenser.getTicket()).isEqualTo("Your Number is 5")
  }
}

package misk.testing.fieldbind

import com.google.inject.testing.fieldbinder.Bind
import misk.testing.MiskTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Provider

@MiskTest
class BindFieldsUsingProviderTest {
  @Inject lateinit var ticketDispenser: TicketDispenserService
  @Bind val counterService: Provider<CountService> = object : Provider<CountService> {
    override fun get(): CountService {
      return FakeCountService(42)
    }
  }

  @Test
  fun `bind CounterService using a provider`() {
    assertThat(ticketDispenser.getTicket()).isEqualTo("Your Number is 42")
  }
}

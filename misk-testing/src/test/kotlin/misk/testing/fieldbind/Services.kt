package misk.testing.fieldbind

import javax.inject.Inject

interface CountService {
  fun getAndInc(): Int
}

class FakeCountService(initial: Int = 0) : CountService {
  var count = initial
  override fun getAndInc(): Int {
    return count++
  }
}

class TicketDispenserService @Inject constructor(
  private val countService: CountService
) {
  fun getTicket(): String {
    return "Your Number is ${countService.getAndInc()}"
  }
}

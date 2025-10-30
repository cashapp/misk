package misk.time

import org.junit.jupiter.api.Test

class ValidateZoneRulesProviderServiceTest {
  @Test
  fun `successful startup when ZoneRulesProvider is available`() {
    // this would throw an error if the class is not available
    val service = ValidateZoneRulesProviderService()
  }
}

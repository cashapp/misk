package misk.time

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Singleton
import java.time.ZoneId
import misk.logging.getLogger

/**
 * Optional service that validates that the ZoneRulesProvider class has been
 * loaded. To use this validator, install it in your service module.
 *
 * This service exists because we've observed cases where the ZoneRulesProvider
 * class infrequently fails to load on a single pod. When this happens, any
 * operation that requires time zone information (like timestamp parsing in
 * database queries) will fail. By installing this service, we can force the
 * container to restart in such cases.
 */
@Singleton
class ValidateZoneRulesProviderService : AbstractIdleService() {
  override fun startUp() {
    try {
      // this uses ZoneRulesProvider.getAvailableZoneIds() under the hood
      val availableZoneIds = ZoneId.getAvailableZoneIds()
    } catch (e: NoClassDefFoundError) {
      logger.error(e) { "ZoneRulesProvider class could not be found" }
      throw e
    } catch (e: ExceptionInInitializerError) {
      logger.error(e) { "Failed to initialize ZoneRulesProvider" }
      throw e
    }
  }

  override fun shutDown() {}

  companion object {
    private val logger = getLogger<ValidateZoneRulesProviderService>()
  }
}

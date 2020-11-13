package misk.time

import com.google.common.util.concurrent.AbstractIdleService
import java.time.ZoneOffset
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Forces the default JVM timezone to UTC.
 *
 * This is useful when running Misk applications on machines that
 * don't have UTC set as the system timezone (eg. development machines).
 */
@Singleton
class ForceUtcTimeZoneService @Inject constructor() : AbstractIdleService() {
  override fun startUp() = TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC))

  override fun shutDown() {}
}

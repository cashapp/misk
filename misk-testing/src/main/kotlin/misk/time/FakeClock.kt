package misk.time

import java.time.Instant
import java.time.ZoneId

class FakeClock @JvmOverloads constructor(
  epochMillis: Long = Instant.parse("2018-01-01T00:00:00Z").toEpochMilli(),
  zone: ZoneId = ZoneId.of("UTC")
) : wisp.time.FakeClock(epochMillis, zone)

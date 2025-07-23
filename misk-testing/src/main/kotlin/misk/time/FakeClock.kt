package misk.time

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import misk.testing.TestFixture
import jakarta.inject.Inject

@Suppress("AnnotatePublicApisWithJvmOverloads")
open class FakeClock (
    epochMillis: Long = initialValue.toEpochMilli(),
    private val zone: ZoneId = ZoneId.of("UTC")
) : Clock(), TestFixture {

    constructor(epochMillis: Long) : this(epochMillis, ZoneId.of("UTC"))

    // Explicit overloads constructors are used so that the 0 parameter constructor can be @Inject annotated for easy injection
    @Inject constructor() : this(initialValue.toEpochMilli(), ZoneId.of("UTC"))
    
    private val millis: AtomicLong = AtomicLong(epochMillis)

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = FakeClock(millis.get(), zone)

    override fun instant(): Instant = Instant.ofEpochMilli(millis.get()).atZone(zone).toInstant()

    /** Advance the clock by specified [Duration]. */
    fun add(d: Duration) = millis.addAndGet(d.toMillis())

    /**
     * Advance the clock by the specified [Period].
     * Note that unlike adding a [Duration] the exact amount that is added to the clock will depend on
     * its current time and timezone. Not all days, months or years have the same length. See the
     * documentation for [Period].
     */
    fun add(p: Period) = millis.updateAndGet { millis ->
        Instant.ofEpochMilli(millis).atZone(zone).plus(p).toInstant().toEpochMilli()
    }

    /** Advance the clock by specified amount `n` of [TimeUnit]. */
    fun add(n: Long, unit: TimeUnit) = millis.addAndGet(TimeUnit.MILLISECONDS.convert(n, unit))

    /** Set the clock to the specified [Instant]. */
    fun setNow(instant: Instant) = millis.set(instant.toEpochMilli())

    override fun reset() {
      setNow(initialValue)
    }

    companion object {
        private val initialValue = Instant.parse("2018-01-01T00:00:00Z")
    }
}

package misk.flags.memory

import misk.flags.Flag
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** In-memory representation of flags, allowing the flag to be programmatically changed by tests */
interface InMemoryFlag<T> : Flag<T> {
    fun set(t: T)
}

class InMemoryBooleanFlag internal constructor(
        override val name: String,
        override val description: String
) : InMemoryFlag<Boolean> {

    private val set = AtomicBoolean()
    private val value = AtomicBoolean()

    override fun get(): Boolean? = if (set.get()) value.get() else null

    override fun set(b: Boolean) {
        value.set(b)
        set.set(true)
    }
}

class InMemoryIntFlag internal constructor(
        override val name: String,
        override val description: String
) : InMemoryFlag<Int> {

    private val set = AtomicBoolean()
    private val value = AtomicInteger()

    override fun get(): Int? = if (set.get()) value.get() else null

    override fun set(n: Int) {
        value.set(n)
        set.set(true)
    }
}

class InMemoryDoubleFlag internal constructor(
        override val name: String,
        override val description: String
) : InMemoryFlag<Double> {

    private val set = AtomicBoolean()
    private val value = AtomicLong()

    override fun get(): Double? = if (set.get()) Double.fromBits(value.get()) else null

    override fun set(n: Double) {
        value.set(n.toBits())
        set.set(true)
    }
}

class InMemoryStringFlag internal constructor(
        override val name: String,
        override val description: String
) : InMemoryFlag<String> {

    private val value = AtomicReference<String>()

    override fun get(): String? = value.get()

    override fun set(s: String) {
        value.set(s)
    }
}

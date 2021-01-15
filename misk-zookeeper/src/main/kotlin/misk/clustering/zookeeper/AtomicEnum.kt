package misk.clustering.zookeeper

import java.util.concurrent.atomic.AtomicInteger

/** An [AtomicEnum] is an enum that can be updated atomically, including an atomic compare and set */
internal class AtomicEnum<E : Enum<E>> private constructor(
  initialValue: E,
  private val enumConstants: Array<E>
) {
  private val ref = AtomicInteger(initialValue.ordinal)

  fun get(): E = enumConstants[ref.get()]

  fun set(value: E) = ref.set(value.ordinal)

  fun compareAndSet(expectedValue: E, newValue: E) = ref.compareAndSet(
      expectedValue.ordinal,
      newValue.ordinal
  )

  fun getAndSet(newValue: E) = enumConstants[ref.getAndSet(newValue.ordinal)]

  companion object {
    inline fun <reified E : Enum<E>> of(initialValue: E) = AtomicEnum(initialValue,
        E::class.java.enumConstants)
  }
}

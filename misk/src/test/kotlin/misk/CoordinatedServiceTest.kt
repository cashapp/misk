package misk

import com.google.common.util.concurrent.AbstractService
import com.google.inject.Key
import com.google.inject.name.Names
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class CoordinatedServiceTest {
  @Test fun happyPath() {
    val target = StringBuilder()

    val a = AppendingService(target, "Service A", produced = setOf("a"))
    val c = AppendingService(target, "Service C", consumed = setOf("a", "b"))
    val b = AppendingService(target, "Service B", produced = setOf("b"))

    val serviceManager = CoordinatedService.coordinate(listOf(a, b, c), listOf())
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    target.append("healthy\n")
    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    assertThat(target.toString()).isEqualTo("""
        |starting Service A
        |starting Service B
        |starting Service C
        |healthy
        |stopping Service C
        |stopping Service A
        |stopping Service B
        |""".trimMargin())
  }

  @Test fun overrideDependencies() {
    val target = StringBuilder()

    val a = AppendingService(target, "Service A", produced = setOf("a"))
    val c = NoDependencyService(target, "Service C")
    val b = AppendingService(target, "Service B", produced = setOf("b"))

    val extraDeps = ServiceDependencyOverride(
        NoDependencyService::class,
        listOf("a", "b").map { nameToKey(it) }.toSet()
    )
    val serviceManager = CoordinatedService.coordinate(listOf(a, b, c), listOf(extraDeps))
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    target.append("healthy\n")
    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    assertThat(target.toString()).isEqualTo("""
        |starting Service A
        |starting Service B
        |starting Service C
        |healthy
        |stopping Service C
        |stopping Service A
        |stopping Service B
        |""".trimMargin())
  }

  @Test fun chainsOfDependencies() {
    val target = StringBuilder()

    val a = AppendingService(target, "Service A", produced = setOf("a"), consumed = setOf())
    val b = AppendingService(target, "Service B", produced = setOf("b"), consumed = setOf("c"))
    val c = AppendingService(target, "Service C", produced = setOf("c"), consumed = setOf("a"))
    val d = AppendingService(target, "Service D", produced = setOf("d"), consumed = setOf("b"))
    val e = AppendingService(target, "Service E", produced = setOf("e"), consumed = setOf("d"))

    val serviceManager = CoordinatedService.coordinate(listOf(a, b, c, d, e), listOf())
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    target.append("healthy\n")
    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    assertThat(target.toString()).isEqualTo("""
        |starting Service A
        |starting Service C
        |starting Service B
        |starting Service D
        |starting Service E
        |healthy
        |stopping Service E
        |stopping Service D
        |stopping Service B
        |stopping Service C
        |stopping Service A
        |""".trimMargin())
  }

  @Test fun treesOfDependencies() {
    val target = StringBuilder()

    val a = AppendingService(target, "Service A", produced = setOf("a"), consumed = setOf())
    val b = AppendingService(target, "Service B", produced = setOf("b"), consumed = setOf())
    val c = AppendingService(target, "Service C", produced = setOf("c"), consumed = setOf("a", "b"))
    val d = AppendingService(target, "Service D", produced = setOf("d"), consumed = setOf("c", "e"))
    val e = AppendingService(target, "Service E", produced = setOf("e"), consumed = setOf("a", "c"))

    val serviceManager = CoordinatedService.coordinate(listOf(a, b, c, d, e), listOf())
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    target.append("healthy\n")
    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    assertThat(target.toString()).isEqualTo("""
        |starting Service A
        |starting Service B
        |starting Service C
        |starting Service E
        |starting Service D
        |healthy
        |stopping Service D
        |stopping Service E
        |stopping Service C
        |stopping Service A
        |stopping Service B
        |""".trimMargin())
  }

  @Test fun unsatisfiedDependency() {
    val target = StringBuilder()

    val a = AppendingService(target, "Service A", produced = setOf("a"))
    val c = AppendingService(target, "Service C", consumed = setOf("a", "b"))

    assertThat(assertFailsWith<IllegalArgumentException> {
      CoordinatedService.coordinate(listOf(a, c), listOf())
    }).hasMessage("""
        |Service dependency graph has problems:
        |  Service C requires ${nameToKey("b")} but no service produces it""".trimMargin())
  }

  @Test fun multipleProducersOfOneDependency() {
    val target = StringBuilder()

    val a = AppendingService(target, "Service A", produced = setOf("a"))
    val c = AppendingService(target, "Service C", consumed = setOf("a"))
    val b = AppendingService(target, "Service B", produced = setOf("a"))

    assertThat(assertFailsWith<IllegalArgumentException> {
      CoordinatedService.coordinate(listOf(a, b, c), listOf())
    }).hasMessage("""
        |Service dependency graph has problems:
        |  multiple services produce ${nameToKey("a")}: Service A and Service B""".trimMargin())
  }

  @Test fun dependencyCycle() {
    val target = StringBuilder()

    val a = AppendingService(target, "Service A", produced = setOf("a"), consumed = setOf("d"))
    val b = AppendingService(target, "Service B", produced = setOf("b"), consumed = setOf("c"))
    val c = AppendingService(target, "Service C", produced = setOf("c"), consumed = setOf("a"))
    val d = AppendingService(target, "Service D", produced = setOf("d"), consumed = setOf("c"))

    assertThat(assertFailsWith<IllegalArgumentException> {
      CoordinatedService.coordinate(listOf(a, b, c, d), listOf())
    }).hasMessage("""
        |Service dependency graph has problems:
        |  dependency cycle: Service A -> Service D -> Service C -> Service A""".trimMargin())
  }

  @Test fun failuresPropagate() {
    val target = StringBuilder()

    val a = object : AbstractService(), DependentService {
      override val consumedKeys: Set<Key<*>> = setOf()
      override val producedKeys: Set<Key<*>> = setOf(nameToKey("a"))
      override fun doStart() = throw Exception("boom!")
      override fun doStop() = Unit
      override fun toString() = "FailingService"
    }

    val b = AppendingService(target, "Service B", consumed = setOf("a"))

    val serviceManager = CoordinatedService.coordinate(listOf(a, b), listOf())
    serviceManager.startAsync()
    assertFailsWith<IllegalStateException> {
      serviceManager.awaitHealthy()
    }
  }

  /** Appends messages to `target` on start up and shut down. */
  class AppendingService(
    val target: StringBuilder,
    val name: String,
    val consumed: Set<String> = setOf(),
    val produced: Set<String> = setOf()
  ) : AbstractService(), DependentService {

    override val consumedKeys: Set<Key<*>>
      get() = consumed.map { nameToKey(it) }.toSet()

    override val producedKeys: Set<Key<*>>
      get() = produced.map { nameToKey(it) }.toSet()

    override fun doStart() {
      target.append("starting $name\n")
      notifyStarted()
    }

    override fun doStop() {
      target.append("stopping $name\n")
      notifyStopped()
    }

    override fun toString() = name
  }

  /** NoDependencyService is the same as AppendingService but has no dependencies. */
  class NoDependencyService(
    val target: StringBuilder,
    val name: String
  ) : AbstractService(), DependentService {
    override val consumedKeys: Set<Key<*>>
      get() = setOf()

    override val producedKeys: Set<Key<*>>
      get() = setOf()

    override fun doStart() {
      target.append("starting $name\n")
      notifyStarted()
    }

    override fun doStop() {
      target.append("stopping $name\n")
      notifyStopped()
    }

    override fun toString() = name
  }
}

private fun nameToKey(it: String) = Key.get(String::class.java, Names.named(it))

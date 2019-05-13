package misk

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.Service
import com.google.inject.Key
import com.google.inject.name.Names
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class ServiceGraphBuilderTest {

  @Test
  fun happyPath() {
    val target = StringBuilder()

    val keyA = key("a")
    val keyB = key("b")
    val keyC = key("c")

    val builder = ServiceGraphBuilder()
    builder.addService(keyA, AppendingService(target, "Service A"))
    builder.addService(keyB, AppendingService(target, "Service B"))
    builder.addService(keyC, AppendingService(target, "Service C"))

    builder.addDependency(keyC, keyA)
    builder.addDependency(keyC, keyB)

    val serviceManager = builder.build()

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

  @Test
  fun enhancer() {
    val target = StringBuilder()

    val keyA = key("a")
    val keyAEnhancement = key("a enhancement")
    val keyC = key("c")

    val builder = ServiceGraphBuilder()
    builder.addService(keyA, AppendingService(target, "Service A"))
    builder.addService(keyC, AppendingService(target, "Service C"))
    builder.addService(keyAEnhancement, AppendingService(target, "Service A enhancement"))

    builder.addEnhancement(keyA, keyAEnhancement)
    builder.addDependency(keyC, keyA)

    val serviceManager = builder.build()

    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    target.append("healthy\n")
    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    assertThat(target.toString()).isEqualTo("""
        |starting Service A
        |starting Service A enhancement
        |starting Service C
        |healthy
        |stopping Service C
        |stopping Service A enhancement
        |stopping Service A
        |""".trimMargin())
  }

  @Test fun chainsOfDependencies() {
    val target = StringBuilder()

    val a = AppendingService(target, "Service A")
    val b = AppendingService(target, "Service B")
    val c = AppendingService(target, "Service C")
    val d = AppendingService(target, "Service D")
    val e = AppendingService(target, "Service E")

    val keyA = key("a")
    val keyB = key("b")
    val keyC = key("c")
    val keyD = key("d")
    val keyE = key("e")

    val builder = ServiceGraphBuilder()
    builder.addService(keyA, a)
    builder.addService(keyB, b)
    builder.addService(keyC, c)
    builder.addService(keyD, d)
    builder.addService(keyE, e)

    builder.addDependency(keyB, keyC)
    builder.addDependency(keyC, keyA)
    builder.addDependency(keyD, keyB)
    builder.addDependency(keyE, keyD)

    val serviceManager = builder.build()
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
    TODO()
//    val target = StringBuilder()
//
//    val a = CoordinatedServiceTest.AppendingService(target, "Service A", produced = setOf("a"),
//        consumed = setOf())
//    val b = CoordinatedServiceTest.AppendingService(target, "Service B", produced = setOf("b"),
//        consumed = setOf())
//    val c = CoordinatedServiceTest.AppendingService(target, "Service C", produced = setOf("c"),
//        consumed = setOf("a", "b"))
//    val d = CoordinatedServiceTest.AppendingService(target, "Service D", produced = setOf("d"),
//        consumed = setOf("c", "e"))
//    val e = CoordinatedServiceTest.AppendingService(target, "Service E", produced = setOf("e"),
//        consumed = setOf("a", "c"))
//
//    val serviceManager = CoordinatedService.coordinate(listOf(a, b, c, d, e))
//    serviceManager.startAsync()
//    serviceManager.awaitHealthy()
//    target.append("healthy\n")
//    serviceManager.stopAsync()
//    serviceManager.awaitStopped()
//
//    assertThat(target.toString()).isEqualTo("""
//        |starting Service A
//        |starting Service B
//        |starting Service C
//        |starting Service E
//        |starting Service D
//        |healthy
//        |stopping Service D
//        |stopping Service E
//        |stopping Service C
//        |stopping Service A
//        |stopping Service B
//        |""".trimMargin())
  }

  @Test fun unsatisfiedDependency() {
    TODO()
//    val target = StringBuilder()
//
//    val a = CoordinatedServiceTest.AppendingService(target, "Service A", produced = setOf("a"))
//    val c = CoordinatedServiceTest.AppendingService(target, "Service C", consumed = setOf("a", "b"))
//
//    assertThat(assertFailsWith<IllegalArgumentException> {
//      CoordinatedService.coordinate(listOf(a, c))
//    }).hasMessage("""
//        |Service dependency graph has problems:
//        |  Service C requires ${key("b")} but no service produces it""".trimMargin())
  }


  @Test fun dependencyCycle() {
    TODO()
//    val target = StringBuilder()
//
//    val a = CoordinatedServiceTest.AppendingService(target, "Service A", produced = setOf("a"),
//        consumed = setOf("d"))
//    val b = CoordinatedServiceTest.AppendingService(target, "Service B", produced = setOf("b"),
//        consumed = setOf("c"))
//    val c = CoordinatedServiceTest.AppendingService(target, "Service C", produced = setOf("c"),
//        consumed = setOf("a"))
//    val d = CoordinatedServiceTest.AppendingService(target, "Service D", produced = setOf("d"),
//        consumed = setOf("c"))
//
//    assertThat(assertFailsWith<IllegalArgumentException> {
//      CoordinatedService.coordinate(listOf(a, b, c, d))
//    }).hasMessage("""
//        |Service dependency graph has problems:
//        |  dependency cycle: Service A -> Service D -> Service C -> Service A""".trimMargin())
  }

  @Test fun failuresPropagate() {
    TODO()
//    val target = StringBuilder()
//
//    val a = object : AbstractService(), DependentService {
//      override val consumedKeys: Set<Key<*>> = setOf()
//      override val producedKeys: Set<Key<*>> = setOf(key("a"))
//      override fun doStart() = throw Exception("boom!")
//      override fun doStop() = Unit
//      override fun toString() = "FailingService"
//    }
//
//    val b = CoordinatedServiceTest.AppendingService(target, "Service B", consumed = setOf("a"))
//
//    val serviceManager = CoordinatedService.coordinate(listOf(a, b))
//    serviceManager.startAsync()
//    assertFailsWith<IllegalStateException> {
//      serviceManager.awaitHealthy()
//    }
  }


  /** Appends messages to `target` on start up and shut down. */
  class AppendingService(
    val target: StringBuilder,
    val name: String
  ) : AbstractService() {

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

  fun key(name: String): Key<*> = Key.get(Service::class.java, Names.named(name))

}
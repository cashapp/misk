package misk

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.Service
import com.google.inject.Key
import com.google.inject.name.Names
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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

  fun key(name: String): Key<*> = Key.get(Service::class.java, Names.named(name))

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
}
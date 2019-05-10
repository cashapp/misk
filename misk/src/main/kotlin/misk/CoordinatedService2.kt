package misk

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.Service.*

internal class CoordinatedService2(val service: Service) : AbstractService() {
  val upstream = mutableSetOf<CoordinatedService2>()   // upstream services dependent on me
  val downstream = mutableSetOf<CoordinatedService2>() // downstream dependencies
  val enhancements = mutableSetOf<CoordinatedService2>()

  init {
    service.addListener(object : Listener() {
      override fun running() {
        println("$service is running!")
        synchronized(this) {
          notifyStarted()
        }
        for (service in downstream) {
          service.startIfReady()
        }
      }

      override fun terminated(from: State?) {
        synchronized(this) {
          notifyStopped()
        }
        for (service in upstream) {
          service.stopIfReady()
        }
      }

      override fun failed(from: State, failure: Throwable) {
        notifyFailed(failure)
      }
    }, MoreExecutors.directExecutor())
  }

  val isUpstreamRunning: Boolean
    get() = upstream.none { it.state() == State.RUNNING }

  val isDownstreamTerminated: Boolean
    get() = downstream.none { it.state() == State.TERMINATED }

  val enhancementsAreRunning: Boolean get() = TODO()

  val enhancementsAreTerminated: Boolean get() = TODO()

  override fun doStart() {
    startIfReady()
  }

  fun startIfReady() {
    synchronized(this) {
      if (state() != State.STARTING || service.state() != State.NEW) return

      // Make sure upstream is ready for us to start.
      if (!isUpstreamRunning) return

      // Actually start.
      service.startAsync()
    }
  }

  override fun doStop() {
    stopIfReady()
  }

  fun stopIfReady() {
    synchronized(this) {
      if (state() != State.STOPPING || service.state() != State.RUNNING) return

      // Make sure downstream is ready for us to stop.
      if (!isDownstreamTerminated) return

      // Actually stop.
      service.stopAsync()
    }
  }

  override fun toString() = service.toString()

  /**
   * Adds the provided list of services as dependents downstream.
   */
  fun addToDownstream(services: List<CoordinatedService2>) {
    // Satisfy all consumers with a producer (maintain bi-directional links up/down-stream)
    downstream.addAll(services)
    services.forEach { it.upstream.add(this) }
  }

  /**
   * Adds the provided list of services as producers upstream.
   *
   * I am not sure if this is useful to have?
   */
  fun addToUpstream(services: List<CoordinatedService2>) {
    // Satisfy all producers with this consumer
    upstream.addAll(services)
    services.forEach { it.downstream.add(this) }
  }

  /**
   * Adds indicated services as "enhancements" to this service.
   *
   * Enhancements will start after the coordinated service is running, and stop before it stops.
   */
  fun enhanceWith(services: List<CoordinatedService2>) {
    enhancements.addAll(services)
    services.forEach { it.addToDownstream(listOf(this)) }
  }

  /**
   * Checks for dependency cycles and throws if one is detected.
   */
  fun requireNoCycles() {
    val errors = mutableListOf<String>()
    val validityMap = mutableMapOf<CoordinatedService2, CycleValidity>()
    for (service in this.downstream) {
      val cycle = service.findCycle(validityMap)
      if (cycle != null) {
        errors.add("dependency cycle: ${cycle.joinToString("->")}")
        break
      }
    }
    require(errors.isEmpty()) {
      "Service dependency graph has problems:\n  ${errors.joinToString("\n  ")}"
    }
  }

  companion object {
    enum class CycleValidity {
      UNKNOWN,
      CHECKING_FOR_CYCLES,
      NO_CYCLES,
    }

    /**
     * Returns the elements of a dependency cycle, or null if there are no cycles originating at
     * a given node.
     */
    fun CoordinatedService2.findCycle(
      validityMap: MutableMap<CoordinatedService2, CycleValidity>
    ): MutableList<CoordinatedService2>? {
      when (validityMap[this]) {
        CycleValidity.NO_CYCLES -> return null // We checked this node already.
        CycleValidity.CHECKING_FOR_CYCLES -> return mutableListOf(this) // We found a cycle!
        else -> {
          validityMap[this] = CycleValidity.CHECKING_FOR_CYCLES
          for (service in downstream) {
            val cycle = service.findCycle(validityMap)
            if (cycle != null) {
              cycle.add(this)
              return cycle
            }
          }
          validityMap[this] = CycleValidity.NO_CYCLES
          return null
        }
      }
    }
  }
}
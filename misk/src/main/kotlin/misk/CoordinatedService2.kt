package misk

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.Service.*

internal class CoordinatedService2(val service: Service) : AbstractService() {
  val upstream = mutableSetOf<CoordinatedService2>()   // upstream services dependent on me
  val downstream = mutableSetOf<CoordinatedService2>() // downstream dependencies
  val enhancements = mutableSetOf<CoordinatedService2>() // services that enhance me
  var target : CoordinatedService2? = null

  init {
    service.addListener(object : Listener() {
      override fun running() {
        println("$service is running!")
        synchronized(this) {
          notifyStarted()
        }
        startDependentServices()
      }

      override fun terminated(from: State?) {
        println("$service is stopping!")

        synchronized(this) {
          notifyStopped()
        }
        stopDependentServices()
      }

      override fun failed(from: State, failure: Throwable) {
        notifyFailed(failure)
      }
    }, MoreExecutors.directExecutor())
  }

  private fun startDependentServices() {
    for (service in downstream) {
      service.startIfReady()
    }
    for (service in enhancements) {
      service.startIfReady()
    }
    target?.startDependentServices()
  }

  private fun stopDependentServices() {
    //target?.stopDependentServices()
    for (service in enhancements) {
      service.stopIfReady()
    }
    for (service in upstream) {
      service.stopIfReady()
    }
  }

  fun isRunningSelf(): Boolean {
    return state() == State.RUNNING
  }

  fun isRunningWithEnhancements(): Boolean {
    return isRunningSelf() && enhancements.all { it.isRunningWithEnhancements() }
  }

  fun isStoppedSelf(): Boolean {
    return state() == State.TERMINATED
  }

  fun isStoppedWithEnhancements(): Boolean {
    return isStoppedSelf() && enhancements.all { it.isStoppedWithEnhancements() }
  }

  fun isUpstreamRunning(): Boolean = upstream.all { it.isRunningWithEnhancements() }

  fun isDownstreamTerminated(): Boolean = downstream.all { it.state() == State.TERMINATED}

  override fun doStart() {
    startIfReady()
  }

  fun startIfReady() {
    synchronized(this) {
      if (state() != State.STARTING || service.state() != State.NEW) return

      // If any upstream service or its enhancements are not running, don't start
      if (upstream.any { !it.isRunningWithEnhancements() }) return
      if (target != null && !target!!.isRunningSelf()) return

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

      // If any downstream service or its enhancements are still running, don't stop
      //if (downstream.any { !it.isStoppedWithEnhancements() }) return
      //if (target != null && !target!!.isStoppedSelf()) return
      if (enhancements.any { !it.isStoppedSelf() }) return
      if (downstream.any { !it.isStoppedWithEnhancements() }) return

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
   * Adds indicated services as "enhancements" to this service.
   *
   * Enhancements will start after the coordinated service is running, and stop before it stops.
   */
  fun enhanceWith(services: List<CoordinatedService2>) {
    enhancements.addAll(services)
    services.forEach { it.target = this }
//    services.forEach { it.addToDownstream(listOf(this)) }
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
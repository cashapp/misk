package misk

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.Service.*
import misk.CoordinatedService2.Companion.CycleValidity

internal class CoordinatedService2(val service: Service) : AbstractService() {
  val upstream = mutableSetOf<CoordinatedService2>()      // upstream services dependent on me
  val downstream = mutableSetOf<CoordinatedService2>()    // downstream dependencies
  val enhancements = mutableSetOf<CoordinatedService2>()  // services that enhance me (depend on me)
  var target: CoordinatedService2? = null                 // service I enhance

  fun getServicesThatINeed(): Set<CoordinatedService2> {
    // target
    // upstream providers
    val neededServices = mutableSetOf<CoordinatedService2>()
    if (target != null) {
      neededServices.add(target!!)
    }
    for (provider in upstream) {
      neededServices.add(provider)
      neededServices.addAll(provider.getTransitiveEnhancements())
    }
    return neededServices
  }

  // obtains all dependencies and enhancements (and their dependencies and enhancements)
  // for this service
  fun getServicesThatNeedMe(): Set<CoordinatedService2> {
    // my enhancements
    // my target's dependencies
    // my target's target's dependencies
    // etc.

    val needsMe = mutableSetOf<CoordinatedService2>()
    needsMe.addAll(enhancements)
    var t: CoordinatedService2? = this
    while (t != null) {
      needsMe.addAll(t.downstream)
      t = t.target
    }
    return needsMe
  }

  private fun getTransitiveEnhancements() : Set<CoordinatedService2> {
    val list = mutableSetOf<CoordinatedService2>()
    list.addAll(enhancements)
    for (enhancement in enhancements) {
      list.addAll(enhancement.getTransitiveEnhancements())
    }
    return list
  }

  init {
    service.addListener(object : Listener() {
      override fun running() {
        synchronized(this) {
          notifyStarted()
        }
        getServicesThatNeedMe().forEach { it.startIfReady() }
      }

      override fun terminated(from: State?) {
        synchronized(this) {
          notifyStopped()
        }
        getServicesThatINeed().forEach { it.stopIfReady() }
      }

      override fun failed(from: State, failure: Throwable) {
        notifyFailed(failure)
      }
    }, MoreExecutors.directExecutor())
  }

  private fun isTerminated(): Boolean {
    return state() == State.TERMINATED
  }

  private fun canStart(): Boolean {
    return getServicesThatINeed().all { it.isRunning() }
  }

  private fun canStop(): Boolean {
    return getServicesThatNeedMe().all { it.isTerminated() }
  }

  fun isUpstreamRunning(): Boolean = upstream.all { it.isRunningWithEnhancements() }

  fun isDownstreamTerminated(): Boolean = downstream.all { it.state() == State.TERMINATED}

  override fun doStart() {
    startIfReady()
  }

  private fun startIfReady() {
    synchronized(this) {
      if (state() != State.STARTING || service.state() != State.NEW) return

      // If any upstream service or its enhancements are not running, don't start
      if (!canStart()) return

      // Actually start.
      service.startAsync()
    }
  }

  override fun doStop() {
    stopIfReady()
  }

  private fun stopIfReady() {
    synchronized(this) {
      if (state() != State.STOPPING || service.state() != State.RUNNING) return

      // If any downstream service or its enhancements are still running, don't stop
      if (!canStop()) return

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
  }

  /**
   * Checks for dependency cycles and throws if one is detected.
   */
//  fun requireNoCycles() {
//    val validityMap = mutableMapOf<CoordinatedService2, CycleValidity>()
//    val cycle = this.findCycle(validityMap)
//    if (cycle != null) {
//      throw IllegalStateException("Detected cycle: ${cycle.joinToString(" -> ")}")
//    }
//  }

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
//          if (target != null) {
//            val cycle = target!!.findCycle(validityMap)
//            if (cycle != null) {
//              cycle.add(this)
//              return cycle
//            }
//          }
          for (enhancement in enhancements) {
            val cycle = enhancement.findCycle(validityMap)
            if (cycle != null) {
              cycle.add(this)
              return cycle
            }
          }
          for (dependency in getServicesThatNeedMe()) {
            val cycle = dependency.findCycle(validityMap)
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
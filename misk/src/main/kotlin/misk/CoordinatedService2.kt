package misk

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.Service.*

/**
 * CoordinatedService2 wraps a Service to defer its start up and shut down until its dependent
 * services are ready.
 *
 * This Service will stall in the `STARTING` state until all upstream services are `RUNNING`.
 * Symmetrically it stalls in the `STOPPING` state until all dependent services are `TERMINATED`.
 *
 * This Service may be "enhanced" by other services. That is to say that if there exists a
 * complementary service that is dependent on this service, it will be started after this service
 * starts, and stopped after this service stops. Information about this service's enhancements is
 * hidden from its dependent services.
 *
 * @param service The Service to wrap.
 */
internal class CoordinatedService2(val service: Service) : AbstractService() {
  private val upstream = mutableSetOf<CoordinatedService2>()      // upstream services dependent on me
  private val downstream = mutableSetOf<CoordinatedService2>()    // downstream dependencies
  private val enhancements = mutableSetOf<CoordinatedService2>()  // services that enhance me (depend on me)
  private var target: CoordinatedService2? = null                 // service I enhance

  init {
    service.addListener(object : Listener() {
      override fun running() {
        synchronized(this) {
          notifyStarted()
        }
        getReliantServices().forEach { it.startIfReady() }
      }

      override fun terminated(from: State?) {
        synchronized(this) {
          notifyStopped()
        }
        getRequiredServices().forEach { it.stopIfReady() }
      }

      override fun failed(from: State, failure: Throwable) {
        notifyFailed(failure)
      }
    }, MoreExecutors.directExecutor())
  }

  /**
   * Returns a set of services that are required by this service.
   *
   * This set consists of the target, all first-level dependencies and each dependency's transitive
   * enhancements. This is the set of services that block start-up of this service.
   */
  fun getRequiredServices(): Set<CoordinatedService2> {
    val requiredServices = mutableSetOf<CoordinatedService2>()
    if (target != null) {
      requiredServices.add(target!!)
    }
    for (provider in upstream) {
      requiredServices.add(provider)
      requiredServices.addAll(provider.getTransitiveEnhancements())
    }
    return requiredServices
  }

  /**
   * Returns a set of all services which rely on this service.
   *
   * This set consists of this service's enhancements, and the entire chain of its target's
   * downstream dependencies. This is the set of services that block shut-down of this service.
   */
  // for this service
  fun getReliantServices(): Set<CoordinatedService2> {
    val reliantServices = mutableSetOf<CoordinatedService2>()
    reliantServices.addAll(enhancements)
    var t: CoordinatedService2? = this
    while (t != null) {
      reliantServices.addAll(t.downstream)
      t = t.target
    }
    return reliantServices
  }

  // Recursively obtains all enhancements of a service.
  private fun getTransitiveEnhancements(): Set<CoordinatedService2> {
    val list = mutableSetOf<CoordinatedService2>()
    list.addAll(enhancements)
    for (enhancement in enhancements) {
      list.addAll(enhancement.getTransitiveEnhancements())
    }
    return list
  }

  /**
   * Adds the provided list of services as dependents downstream.
   *
   * @param services List of dependencies for this service.
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
   *
   * @param services List of "enhancements" for this service.
   */
  fun enhanceWith(services: List<CoordinatedService2>) {
    enhancements.addAll(services)
    services.forEach { it.target = this }
  }

  private fun isTerminated(): Boolean {
    return state() == State.TERMINATED
  }

  private fun canStart(): Boolean {
    return getRequiredServices().all { it.isRunning() }
  }

  private fun canStop(): Boolean {
    return getReliantServices().all { it.isTerminated() }
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
   * Traverses the dependency/enhancement graph to detect cycles. If a cycle is found, then a
   * list of services that forms this cycle is returned.
   *
   * @param validityMap: A map that is used to track traversal of this service's dependency graph.
   */
  fun findCycle(
    validityMap: MutableMap<CoordinatedService2, CycleValidity>
  ): MutableList<CoordinatedService2>? {
    when (validityMap[this]) {
      CycleValidity.NO_CYCLES -> return null // We checked this node already.
      CycleValidity.CHECKING_FOR_CYCLES -> return mutableListOf(this) // We found a cycle!
      else -> {
        validityMap[this] = CycleValidity.CHECKING_FOR_CYCLES
        // first check there are no cycles in the enhancements that could cause
        // getReliantServices() to get stuck
        for (enhancement in enhancements) {
          val cycle = enhancement.findCycle(validityMap)
          if (cycle != null) {
            cycle.add(this)
            return cycle
          }
        }
        // now check that there are no mixed enhancement-dependency cycles
        for (dependency in getReliantServices()) {
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

  companion object {
    /**
     * CycleValidity provides states used to track dependency graph traversal and cycle detection.
     */
    enum class CycleValidity {
      CHECKING_FOR_CYCLES,
      NO_CYCLES,
    }
  }
}
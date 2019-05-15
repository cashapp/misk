package misk

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.Service.Listener
import com.google.common.util.concurrent.Service.State

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
  /** Services that start before this. */
  private val upstream = mutableSetOf<CoordinatedService2>()

  /** Services this starts before. */
  private val downstream = mutableSetOf<CoordinatedService2>()

  /** Services that enhance this. This starts before them, but they start before [downstream].*/
  private val enhancements = mutableSetOf<CoordinatedService2>()

  /** Service that starts up before this, and whose [downstream] also depend on this. */
  private var target: CoordinatedService2? = null

  init {
    service.addListener(object : Listener() {
      override fun running() {
        synchronized(this) {
          notifyStarted()
        }
        reliantServices.forEach { it.startIfReady() }
      }

      override fun terminated(from: State?) {
        synchronized(this) {
          notifyStopped()
        }
        requiredServices.forEach { it.stopIfReady() }
      }

      override fun failed(from: State, failure: Throwable) {
        notifyFailed(failure)
      }
    }, MoreExecutors.directExecutor())
  }

  /**
   * Returns a set of services that are required by this service.
   *
   * The set consists of the target, all first-level dependencies and each dependency's transitive
   * enhancements. It is the set of services that block start-up of this service.
   */
  val requiredServices: Set<CoordinatedService2> by lazy {
    val result = mutableSetOf<CoordinatedService2>()
    if (target != null) {
      result += target!!
    }
    for (provider in upstream) {
      result += provider
      provider.getTransitiveEnhancements(result)
    }
    result
  }

  private fun getTransitiveEnhancements(list: MutableSet<CoordinatedService2>) {
    list.addAll(enhancements)
    for (enhancement in enhancements) {
      enhancement.getTransitiveEnhancements(list)
    }
  }

  /**
   * Returns a set of all services which require this service to be started before they can start,
   * and who must shut down before this service shuts down.
   *
   * The set contains this service's enhancements, its dependencies, and the dependencies of target
   * (if it exists) and all transitive targets.
   */
  val reliantServices: Set<CoordinatedService2> by lazy {
    val result = mutableSetOf<CoordinatedService2>()
    result += enhancements
    var t: CoordinatedService2? = this
    while (t != null) {
      result += t.downstream
      t = t.target
    }
    result
  }

  /**
   * Adds the provided list of services as dependents downstream.
   *
   * @param services List of dependencies for this service.
   */
  fun addDependencies(services: List<CoordinatedService2>) {
    downstream += services
    services.forEach { it.upstream += this }
  }

  /**
   * Adds indicated services as "enhancements" to this service.
   *
   * Enhancements will start after the coordinated service is running, and stop before it stops.
   *
   * @param services List of "enhancements" for this service.
   */
  fun addEnhancements(services: List<CoordinatedService2>) {
    enhancements.addAll(services)
    services.forEach { it.target = this }
  }

  private fun isTerminated(): Boolean {
    return state() == State.TERMINATED
  }

  private fun canStart(): Boolean {
    return requiredServices.all { it.isRunning() }
  }

  private fun canStop(): Boolean {
    return reliantServices.all { it.isTerminated() }
  }

  override fun doStart() {
    startIfReady()
  }

  private fun startIfReady() {
    synchronized(this) {
      if (state() != State.STARTING || service.state() != State.NEW) return

      // If any upstream service or its enhancements are not running, don't start.
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

      // If any downstream service or its enhancements are still running, don't stop.
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
        // First check there are no cycles in the enhancements that could cause
        // getReliantServices() to get stuck.
        for (enhancement in enhancements) {
          val cycle = enhancement.findCycle(validityMap)
          if (cycle != null) {
            cycle.add(this)
            return cycle
          }
        }
        // Now check that there are no mixed enhancement-dependency cycles.
        for (dependency in reliantServices) {
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
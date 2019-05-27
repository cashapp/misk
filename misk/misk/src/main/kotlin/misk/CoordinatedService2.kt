package misk

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.Service.Listener
import com.google.common.util.concurrent.Service.State
import javax.inject.Provider

/**
 * Services in Misk can depend on other services.
 *
 * ### Dependencies
 *
 * Suppose we have a DatabaseService and a MovieService, with the MovieService depending on the
 * DatabaseService.
 *
 * ```
 * DatabaseService
 *   depended on by MovieService
 * ```
 *
 * This class manages startup and shutdown of the each service, so that a service can only run when
 * the services it depends on are running. In the example above, the MovieService doesn't enter the
 * `STARTING` state until the DatabaseService has entered the `RUNNING` state. Conversely, the
 * MovieService must enter the `TERMINATED` state before the DatabaseService enters the `STOPPING`
 * state.
 *
 * Dependencies can have their own dependencies, so there's an entire graph to manage of what starts
 * and stops when.
 *
 * ### Enhancements
 *
 * Some services exist to enhance the behavior of another service. For example, a DatabaseService
 * may manage a generic connection to a MySQL database, and the SchemaMigrationService may create
 * tables specific to the application.
 *
 * We treat such enhancements as implementation details of the enhanced service: they depend on the
 * service, but downstream dependencies like the MovieService don't need to know that they exist.
 *
 * ```
 * DatabaseService
 *   enhanced by SchemaMigrationService
 *   depended on by MovieService
 * ```
 *
 * In the above service graph we start the DatabaseService first, the SchemaMigrationService second,
 * and finally the MovieService. The MovieService doesn't need to express a dependency on the
 * SchemaMigrationService, that happens automatically for enhancements.
 *
 * ### How It Works
 *
 * CoordinatedService2 wraps the actual Service to defer its start up and shut down until its
 * dependent services are ready.
 *
 * This Service will stall in the `STARTING` state until all upstream services are `RUNNING`.
 * Symmetrically it stalls in the `STOPPING` state until all dependent services are `TERMINATED`.
 */
internal class CoordinatedService2(
    private val serviceProvider : Provider<out Service>
) : AbstractService() {

  val service : Service by lazy {
    serviceProvider.get()
  }

  /** Services that start before this. */
  private val directDependsOn = mutableSetOf<CoordinatedService2>()

  /** Services this starts before. */
  private val directDependencies = mutableSetOf<CoordinatedService2>()

  /**
   * Services that enhance this. This starts before them, but they start before
   * [directDependencies].
   */
  private val enhancements = mutableSetOf<CoordinatedService2>()

  /** Service that starts up before this, and whose [directDependencies] also depend on this. */
  private var enhancementTarget: CoordinatedService2? = null

  init {
    service.checkNew("$service must be NEW for it to be coordinated")
    service.addListener(object : Listener() {
      override fun running() {
        synchronized(this) {
          notifyStarted()
        }
        downstreamServices.forEach { it.startIfReady() }
      }

      override fun terminated(from: State) {
        synchronized(this) {
          notifyStopped()
        }
        upstreamServices.forEach { it.stopIfReady() }
      }

      override fun failed(from: State, failure: Throwable) {
        notifyFailed(failure)
      }
    }, MoreExecutors.directExecutor())
  }

  /**
   * Returns a set of services that are required by this service.
   *
   * The set consists of the [enhancementTarget], all direct dependencies and each dependency's
   * transitive enhancements. It is the set of services that block start-up of this service.
   */
  val upstreamServices: Set<CoordinatedService2> by lazy {
    val result = mutableSetOf<CoordinatedService2>()
    if (enhancementTarget != null) {
      result += enhancementTarget!!
    }
    for (provider in directDependsOn) {
      result += provider
      provider.getTransitiveEnhancements(result)
    }
    result
  }

  private fun getTransitiveEnhancements(sink: MutableSet<CoordinatedService2>) {
    sink += enhancements
    for (enhancement in enhancements) {
      enhancement.getTransitiveEnhancements(sink)
    }
  }

  /**
   * Returns a set of all services which require this service to be started before they can start,
   * and who must shut down before this service shuts down.
   *
   * The set contains this service's enhancements, its dependencies, and the dependencies of
   * [enhancementTarget] (if it exists) and all transitive targets.
   */
  val downstreamServices: Set<CoordinatedService2> by lazy {
    val result = mutableSetOf<CoordinatedService2>()
    result += enhancements
    var t: CoordinatedService2? = this
    while (t != null) {
      result += t.directDependencies
      t = t.enhancementTarget
    }
    result
  }

  /** Adds [services] as dependents downstream. */
  fun addDependentServices(vararg services: CoordinatedService2) {
    // Check that this service and all dependent services are new before modifying the graph.
    this.checkNew()
    for (service in services) {
      service.checkNew()
      directDependencies += service
      service.directDependsOn += this
    }
  }

  /**
   * Adds [services] as enhancements to this service. Enhancements will start after the coordinated
   * service is running, and stop before it stops.
   */
  fun addEnhancements(vararg services: CoordinatedService2) {
    // Check that this service and all dependent services are new before modifying the graph.
    this.checkNew()
    for (service in services) {
      service.checkNew()
      enhancements += service
      service.enhancementTarget = this
    }
  }

  private fun isTerminated(): Boolean {
    return state() == State.TERMINATED
  }

  override fun doStart() {
    startIfReady()
  }

  private fun startIfReady() {
    synchronized(this) {
      if (state() != State.STARTING || service.state() != State.NEW) return

      // If any upstream service or its enhancements are not running, don't start.
      if (upstreamServices.any { !it.isRunning() }) return

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

      // If any downstream service or its enhancements haven't stopped, don't stop.
      if (downstreamServices.any { !it.isTerminated() }) return

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
        // getDownstreamServices() to get stuck.
        for (enhancement in enhancements) {
          val cycle = enhancement.findCycle(validityMap)
          if (cycle != null) {
            cycle.add(this)
            return cycle
          }
        }
        // Now check that there are no mixed enhancement-dependency cycles.
        for (dependency in downstreamServices) {
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

    /**
     * Extension to check that a given [Service] is `NEW`.
     */
    private fun Service.checkNew(
      message: String = "Cannot add dependencies after the service graph has been built"
    ) {
      check(state() == State.NEW) { message }
    }

  }
}
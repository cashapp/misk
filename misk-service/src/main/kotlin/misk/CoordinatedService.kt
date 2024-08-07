package misk

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.Service.Listener
import com.google.common.util.concurrent.Service.State
import com.google.inject.Provider
import java.util.concurrent.atomic.AtomicBoolean

data class CoordinatedServiceMetadata(
  val dependencies: Set<String>,
  val directDependsOn: Set<String>,
)

internal class CoordinatedService(
  private val serviceProvider: Provider<out Service>
) : AbstractService(), DelegatingService {
  override val service: Service by lazy {
    serviceProvider.get()
      .also { created ->
        created.checkNew("$created must be NEW for it to be coordinated")
        created.addListener(
          object : Listener() {
            val outerService = this@CoordinatedService

            override fun running() {
              outerService.notifyStarted()
              dependencies.forEach { it.startIfReady() }
            }

            override fun terminated(from: State) {
              outerService.notifyStopped()
              directDependsOn.forEach { it.stopIfReady() }
            }

            override fun failed(from: State, failure: Throwable) {
              outerService.notifyFailed(failure)
              directDependsOn.forEach { it.stopIfReady() }
            }
          },
          MoreExecutors.directExecutor()
        )
      }
  }

  /** Services this starts before aka enhancements or services who depend on this. */
  private val directDependsOn = mutableSetOf<CoordinatedService>()

  /** Services this starts after aka dependencies. */
  private val dependencies = mutableSetOf<CoordinatedService>()

  /**
   * Used to track internally if this [CoordinatedService] has invoked [startAsync] on the inner
   * [Service] [service]
   * */
  private val innerServiceStarted = AtomicBoolean(false)

  /**
   * Marks every [services] as a dependency and marks itself as directDependsOn on each service.
   * */
  fun addDependentServices(vararg services: CoordinatedService) {
    // Check that this service and all dependent services are new before modifying the graph.
    this.checkNew()
    for (service in services) {
      service.checkNew()
      dependencies += service
      service.directDependsOn += this
    }
  }

  private fun isTerminatedOrFailed(): Boolean {
    return state() == State.TERMINATED || state() == State.FAILED
  }

  override fun doCancelStart() {
    // If not started, skip the inner service and attempt to stop.
    val started = innerServiceStarted.getAndSet(true)
    if (!started) {
      stopIfReady()
    }
  }

  override fun doStart() {
    startIfReady()
  }

  private fun startIfReady() {
    val canStartInner = state() == State.STARTING && directDependsOn.all { it.isRunning }

    // startAsync must be called exactly once
    if (canStartInner) {
      val started = innerServiceStarted.getAndSet(true)
      if (!started) {
        service.startAsync()
      }
    }
  }

  override fun doStop() {
    stopIfReady()
  }

  private fun stopIfReady() {
    val canStopInner =
      state() == State.STOPPING && dependencies.all { it.isTerminatedOrFailed() }

    // stopAsync can be called multiple times, with subsequent calls being ignored
    if (canStopInner) {
      service.stopAsync()
    }
  }

  override fun toString() = service.toString()

  /**
   * Traverses the dependency graph to detect cycles. If a cycle is found, then a
   * list of services that forms this cycle is returned.
   *
   * @param validityMap: A map that is used to track traversal of this service's dependency graph.
   */
  fun findCycle(
    validityMap: MutableMap<CoordinatedService, CycleValidity>
  ): MutableList<CoordinatedService>? {
    when (validityMap[this]) {
      CycleValidity.NO_CYCLES -> return null // We checked this node already.
      CycleValidity.CHECKING_FOR_CYCLES -> return mutableListOf(this) // We found a cycle!
      else -> {
        validityMap[this] = CycleValidity.CHECKING_FOR_CYCLES
        for (dependency in dependencies) {
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

  fun toMetadata() = CoordinatedServiceMetadata(
    dependencies = dependencies.map { it.serviceProvider.get().javaClass.name }.toSet(),
    directDependsOn = directDependsOn.map { it.serviceProvider.get().javaClass.name }.toSet(),
  )
}

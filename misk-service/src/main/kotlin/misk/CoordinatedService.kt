package misk

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.Atomics
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.Service.Listener
import com.google.common.util.concurrent.Service.State
import com.google.inject.Key
import com.google.inject.Provider
import com.google.inject.name.Named
import misk.inject.toKey
import java.util.concurrent.atomic.AtomicBoolean

data class CoordinatedServiceMetadata(
  val dependencies: Set<String>,
  val directDependsOn: Set<String>,
)

internal inline fun <reified T : Service> CoordinatedService(serviceProvider: Provider<T>) = CoordinatedService(
  key = T::class.toKey(),
  serviceProvider = serviceProvider
)

internal class CoordinatedService(
  private val key: Key<*>,
  private val serviceProvider: Provider<out Service>
) : AbstractService(), DelegatingService {

  /**
   * To avoid accessing the by lazy service property for toString, use key until the service
   * is initiated by the service graph.  This will be replaced with service.toString() during
   * by lazy initialization.
   */
  private val toStringLazy = Atomics.newReference<() -> String> {
    buildString {
      key.annotationType?.let {
        if (key.annotation is Named) {
          append((key.annotation as Named).value)
        } else {
          append("@" + key.annotationType.simpleName + " ")
          append(key.typeLiteral.rawType.simpleName)
        }
      } ?: append(key.typeLiteral.rawType.simpleName)

      append(" [${this@CoordinatedService.state()}]")
      if (this@CoordinatedService.state() == State.FAILED) {
        append(" caused by: ${this@CoordinatedService.failureCause()}")
      }
    }
  }

  /**
   * Use care when accessing this value as it will instantiate the instance of the class at this
   * time.  We want the instantiation to follow the Coordinated Service Graph.
   */
  override val service: Service by lazy {
    val realService =
      runCatching {
        serviceProvider.get()
      }
      .onFailure {
        this@CoordinatedService.notifyFailed(it)
      }
      .getOrThrow()
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

    toStringLazy.set {
      realService.toString()
    }

    realService
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
   * Marks every [coordinatedServices] as a dependency and marks itself as
   * directDependsOn on each service.
   * */
  fun addDependentServices(vararg coordinatedServices: CoordinatedService) {
    // Check that this service and all dependent services are new before modifying the graph.
    this.checkNew()
    for (coordinatedService in coordinatedServices) {
      coordinatedService.checkNew()
      dependencies += coordinatedService
      coordinatedService.directDependsOn += this
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

  /**
   * Get the service name from the current string source.  This uses the key until the service
   * is Injected to avoid early injecting the service out of band from the service graph.
   */
  override fun toString() = toStringLazy.get().invoke()

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

  fun toMetadataProvider() = Provider {
    CoordinatedServiceMetadata(
      dependencies = dependencies.map { it.serviceProvider.get().javaClass.name }.toSet(),
      directDependsOn = directDependsOn.map { it.serviceProvider.get().javaClass.name }.toSet(),
    )
  }
}

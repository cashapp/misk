package misk

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Key

/**
 * Wraps a service to defer start up and shut down until dependent services are ready.
 *
 * This service stalls in `STARTING` until all upstream services are `RUNNING`, then it actually
 * starts up. Symmetrically it stalls in `STOPPING` until all downstream services are `TERMINATED`,
 * then it actually shuts down.
 */
internal class CoordinatedService(val service: Service) : AbstractService() {
  val producedKeys: Set<Key<*>>
    get() {
      service as? DependentService ?: return setOf()
      return service.producedKeys
    }

  val consumedKeys: Set<Key<*>>
    get() {
      service as? DependentService ?: return setOf()
      return service.consumedKeys
    }

  val upstream = mutableSetOf<CoordinatedService>()
  val downstream = mutableSetOf<CoordinatedService>()

  init {
    service.addListener(object : Service.Listener() {
      override fun running() {
        synchronized(this) {
          notifyStarted()
        }
        for (service in downstream) {
          service.startIfReady()
        }
      }

      override fun terminated(from: Service.State?) {
        synchronized(this) {
          notifyStopped()
        }
        for (service in upstream) {
          service.stopIfReady()
        }
      }
    }, MoreExecutors.directExecutor())
  }

  override fun doStart() {
    startIfReady()
  }

  fun startIfReady() {
    synchronized(this) {
      if (state() != Service.State.STARTING) return

      // Make sure upstream is ready for us to start.
      for (value in upstream) {
        if (value.state() != Service.State.RUNNING) return
      }

      // Actually start.
      service.startAsync()
    }
  }

  override fun doStop() {
    stopIfReady()
  }

  fun stopIfReady() {
    synchronized(this) {
      if (state() != Service.State.STOPPING) return

      // Make sure downstream is ready for us to stop.
      for (value in downstream) {
        if (value.state() != Service.State.TERMINATED) return
      }

      // Actually stop.
      service.stopAsync()
    }
  }

  override fun toString() = service.toString()

  companion object {
    fun coordinate(services: List<Service>): ServiceManager {
      val coordinatedServices = services.map { CoordinatedService(it) }
      val errors = mutableListOf<String>()

      // Index services by producers.
      val producersMap = mutableMapOf<Key<*>, CoordinatedService>()
      for (service in coordinatedServices) {
        for (key in service.producedKeys) {
          val replaced = producersMap.put(key, service)
          if (replaced != null) {
            errors.add("multiple services produce $key: $replaced and $service")
          }
        }
      }

      // Satisfy all consumers with a producer.
      for (service in coordinatedServices) {
        for (key in service.consumedKeys) {
          val producer = producersMap[key]
          if (producer == null) {
            errors.add("$service requires $key but no service produces it")
            continue
          }
          service.upstream.add(producer)
          producer.downstream.add(service)
        }
      }

      val validityMap = mutableMapOf<CoordinatedService, CycleValidity>()
      for (service in coordinatedServices) {
        val cycle = service.findCycle(validityMap)
        if (cycle != null) {
          errors.add("dependency cycle: ${cycle.joinToString(separator = " -> ")}")
          break
        }
      }

      require(errors.isEmpty()) {
        "Service dependency graph has problems:\n  ${errors.joinToString(separator = "\n  ")}"
      }

      return ServiceManager(coordinatedServices)
    }

    /** Returns the elements of a cycle, or null if there are no cycles originating at `node`. */
    fun CoordinatedService.findCycle(
      validityMap: MutableMap<CoordinatedService, CycleValidity>
    ): MutableList<CoordinatedService>? {
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

    enum class CycleValidity {
      UNKNOWN, CHECKING_FOR_CYCLES, NO_CYCLES,
    }
  }
}
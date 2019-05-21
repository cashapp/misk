package misk

import com.google.common.collect.LinkedHashMultimap
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Key
import misk.CoordinatedService2.Companion.CycleValidity

/**
 * Builds a graph of `CoordinatedService2`s which defer start up and shut down until their dependent
 * services are ready.
 */
class ServiceGraphBuilder {
  private var serviceMap = mutableMapOf<Key<*>, CoordinatedService2>()
  private val dependencyMap = LinkedHashMultimap.create<Key<*>, Key<*>>()
  private val enhancementMap = mutableMapOf<Key<*>, Key<*>>()
  /**
   * Registers a [service] with this [ServiceGraphBuilder]
   *
   * A service should be added before dependencies or enhancements are specified.
   * Keys must be unique. If a key is reused, then the original key-service pair will be replaced.
   */
  fun addService(key: Key<*>, service: Service) {
    serviceMap[key] = CoordinatedService2(service)
  }

  /**
   * Adds the [dependency] service as a dependency to the [service] provider service.
   *
   * @param dependency The identifier for the dependent service.
   * @param service The identifier for the service that provides for `dependency`.
   */
  fun addDependency(service: Key<*>, dependency: Key<*>) {
    dependencyMap.put(service, dependency)
  }

  /**
   * Adds a [enhancement] enhancement to the [service] service. The [service] service depends on its
   * enhancements.
   *
   * Service enhancements [enhancement] will be started after the [service] service is started,
   * but before any of the [service] service's dependents can start. Conversely, the dependents of
   * the service service will be shut down, followed by all [enhancement] enhancements, and finally
   * the [service] service itself.
   *
   * @param service The identifier for the service to be enhanced by [enhancement].
   * @param enhancement The identifier for the service that depends on [service].
   * @throws IllegalStateException if the enhancement has already been applied to another service.
   */
  fun enhanceService(service: Key<*>, enhancement: Key<*>) {
    check(enhancementMap[enhancement] == null) {
      "Enhancement $enhancement cannot be applied more than once"
    }
    enhancementMap[enhancement] = service
  }

  /**
   * Validates the Service Graph is a valid DAG, then builds a ServiceManager.
   *
   * @return ServiceManager that coordinates all services that were registered with this builder.
   * @throws IllegalStateException if the graph is not valid.
   */
  fun build(): ServiceManager {
    validateDependencyMap()
    linkDependencies()
    checkCycles()
    return ServiceManager(serviceMap.values)
  }

  /**
   * Builds CoordinatedService2s from the instructions provided in the dependency and enhancement
   * maps.
   */
  private fun linkDependencies() {
    // First apply enhancements.
    for ((enhancementKey, serviceKey) in enhancementMap) {
      val service = serviceMap[serviceKey]!!
      val enhancement = serviceMap[enhancementKey]!!
      service.addEnhancements(enhancement)
    }

    // Now handle regular dependencies.
    for ((key, service) in serviceMap) {
      val dependencies = dependencyMap[key]?.map { serviceMap[it]!! } ?: listOf()
      service.addDependencies(*dependencies.toTypedArray())
    }
  }

  /**
   * Checks that no service in this builder has specified a dependency cycle.
   */
  private fun checkCycles() {
    val validityMap = mutableMapOf<CoordinatedService2, CycleValidity>()

    for (service in serviceMap.values) {
      val cycle = service.findCycle(validityMap)
      check(cycle == null) { "Detected cycle: ${cycle!!.joinToString(" -> ")}" }
    }
  }

  /**
   * Checks that each service registered with this builder has its dependencies registered.
   * (i.e. no one service requires a dependency or enhancement that doesn't exist.)
   */
  private fun validateDependencyMap() {
    for ((service, dependents) in dependencyMap.asMap()) {
      check(serviceMap[service] != null) {
        val stringBuilder = StringBuilder()
        for (dependent in dependents) {
          stringBuilder.append("${serviceMap[dependent]}")
        }
        "$stringBuilder requires $service but no such service was registered with the builder"
      }
    }
  }
}
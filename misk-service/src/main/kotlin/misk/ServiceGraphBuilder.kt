package misk

import com.google.common.collect.LinkedHashMultimap
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Key
import misk.CoordinatedService.Companion.CycleValidity
import javax.inject.Provider

/**
 * Builds a graph of [CoordinatedService]s which defer start up and shut down until their dependent
 * services are ready.
 */
internal class ServiceGraphBuilder {
  private var serviceMap = mutableMapOf<Key<*>, CoordinatedService>()
  private val dependencyMap = LinkedHashMultimap.create<Key<*>, Key<*>>()
  private val enhancementMap = mutableMapOf<Key<*>, Key<*>>()

  /**
   * Registers a [service] with this [ServiceGraphBuilder]
   *
   * A service should be added before dependencies or enhancements are specified.
   * Keys must be unique. If a key is reused, then the original key-service pair will be replaced.
   */
  fun addService(key: Key<*>, service: Service) {
    addService(key, Provider<Service> { service })
  }

  fun addService(key: Key<*>, serviceProvider: Provider<out Service>) {
    check(serviceMap[key] == null) {
      "Service $key cannot be registered more than once"
    }
    serviceMap[key] = CoordinatedService(serviceProvider)
  }

  /**
   * Registers a dependency pair with the service graph. Specifies that the [dependent] service must
   * start after [dependsOn], and conversely that [dependent] must stop before [dependsOn].
   */
  fun addDependency(dependent: Key<*>, dependsOn: Key<*>) {
    dependencyMap.put(dependsOn, dependent)
  }

  /**
   * Adds a [enhancement] to the service [toBeEnhanced]. The service [toBeEnhanced] depends on its
   * enhancements.
   *
   * Service [enhancement]s will be started after the service [toBeEnhanced] is started, but before
   * any of its dependents can start. Conversely, the dependents of the service [toBeEnhanced] will
   * be shut down, followed by all of its [enhancement]s, and finally the service [toBeEnhanced]
   * itself.
   *
   * @param toBeEnhanced The identifier for the service to be enhanced by [enhancement].
   * @param enhancement The identifier for the service that depends on [toBeEnhanced].
   * @throws IllegalStateException if the enhancement has already been applied to another service.
   */
  fun enhanceService(toBeEnhanced: Key<*>, enhancement: Key<*>) {
    check(enhancementMap[enhancement] == null) {
      "Enhancement $enhancement cannot be applied more than once"
    }
    enhancementMap[enhancement] = toBeEnhanced
  }

  /**
   * Validates the service graph is a valid DAG, then builds a [ServiceManager].
   *
   * @throws IllegalStateException if the graph is not valid.
   */
  fun build(): ServiceManager {
    validateDependencyMap()
    linkDependencies()
    checkCycles()
    return ServiceManager(serviceMap.values)
  }

  /**
   * Builds [CoordinatedService]s from the instructions provided in the dependency and enhancement
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
      service.addDependentServices(*dependencies.toTypedArray())
    }
  }

  /**
   * Checks that no service in this builder has specified a dependency cycle.
   */
  private fun checkCycles() {
    val validityMap = mutableMapOf<CoordinatedService, CycleValidity>()

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

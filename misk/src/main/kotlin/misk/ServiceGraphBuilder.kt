package misk

import com.google.common.collect.HashMultimap
import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.SetMultimap
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
  private val enhancementMap = LinkedHashMultimap.create<Key<*>, Key<*>>()

  /**
   * Registers a [service] with this [ServiceGraphBuilder]
   *
   * A service should be added before dependencies or enhancements are specified.
   * Keys must be unique. If a key is reused, then the original key-service pair will be replaced.
   *
   * @param key A Guice Key used to identify the service.
   * @param service The Service to register with this ServiceGraphBuilder.
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
   */
  fun enhanceService(service: Key<*>, enhancement: Key<*>) {
    enhancementMap.put(service, enhancement)
  }

  /**
   * Validates the Service Graph is a valid DAG, then builds a ServiceManager.
   *
   * @return ServiceManager that coordinates all services that were registered with this builder.
   * @throws IllegalStateException if the graph is not valid.
   */
  fun build(): ServiceManager {
    validateDependencyMap()
    validateEnhancementMap()
    linkDependencies()
    checkCycles()
    return ServiceManager(serviceMap.values)
  }

  /**
   * Builds CoordinatedService2s from the instructions provided in the dependency and enhancement
   * maps.
   */
  private fun linkDependencies() {
    // For each service, add its dependencies and ensure no dependency cycles.
    for ((key, service) in serviceMap) {
      // First get the enhancements for the service and handle their downstream dependencies.
      val enhancements = enhancementMap[key]?.map { serviceMap[it]!! } ?: listOf()
      service.addEnhancements(enhancements)

      // Now handle regular dependencies.
      val dependencies = dependencyMap[key]?.map { serviceMap[it]!! } ?: listOf()
      service.addDependencies(dependencies)
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

  /**
   * Checks that no enhancement has been applied more than once.
   */
  private fun validateEnhancementMap() {
    val enhancementList = mutableListOf<Key<*>>()
    enhancementList += enhancementMap.values()
    enhancementList.groupingBy { it }.eachCount().forEach {
      check(it.value <= 1) { "Enhancement ${it.key} cannot be applied more than once" }
    }
  }
}
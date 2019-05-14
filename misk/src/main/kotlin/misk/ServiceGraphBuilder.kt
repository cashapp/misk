package misk

import com.google.common.collect.HashMultimap
import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.SetMultimap
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Key
import java.lang.IllegalStateException

/**
 * Builds a graph of `CoordinatedService2`s which defer start up and shut down until their dependent
 * services are ready.
 */
class ServiceGraphBuilder {

  private var serviceMap = mutableMapOf<Key<*>, CoordinatedService2>() // map of all services
  private val dependencyMap = LinkedHashMultimap.create<Key<*>, Key<*>>()
  private val enhancementMap = LinkedHashMultimap.create<Key<*>, Key<*>>()

  /**
   * Registers a service with this ServiceGraphBuilder.
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
   * Adds the (small) service as a dependency to the (big) provider service.
   *
   * @param small The identifier for the dependent service.
   * @param big The identifier for the service that provides for `small`.
   */
  fun addDependency(small: Key<*>, big: Key<*>) {
    // maintain a set of dependents on the provider
    val dependencySet = dependencyMap[big]
    dependencySet!!.add(small)
  }

  /**
   * Adds a (small) enhancement to the (big) service. The (big) service depends on its enhancements.
   *
   * Service enhancements (small) will be started after the (big) service is started, but before any
   * of the (big) service's dependents can start. Conversely, the dependents of the big service will
   * be shut down, followed by all (small) enhancements, and finally the (big) service itself.
   *
   * @param big The identifier for the service to be enhanced by `small`.
   * @param small The identifier for the service that depends on `big`.
   */
  fun addEnhancement(big: Key<*>, small: Key<*>) {
    // maintain enhancements to be applied to services
    val enhancementSet = enhancementMap[big]
    enhancementSet!!.add(small)
  }

  /**
   * Validates the Service Graph is a valid DAG, then builds a ServiceManager.
   *
   * @return ServiceManager that coordinates all services that were registered with this builder.
   * @throws IllegalStateException if the graph is not valid.
   */
  fun build(): ServiceManager {
    check(serviceMap.isNotEmpty()) {
      "ServiceGraphBuilder cannot be built without registering services"
    }
    validateDependencyMap()
    validateEnhancementMap()
    linkDependencies()
    checkCycles()
    return ServiceManager(serviceMap.values)
  }

  // Builds CoordinatedServices from the instructions provided in the dependency map.
  private fun linkDependencies() {
    // For each service, add its dependencies and ensure no dependency cycles.
    for ((key, service) in serviceMap) {
      // First get the enhancements for the service and handle their downstream dependencies.
      val enhancements = enhancementMap[key]?.map { serviceMap[it]!! } ?: listOf()
      service.enhanceWith(enhancements)

      // Now handle regular dependencies.
      val dependencies = dependencyMap[key]?.map { serviceMap[it]!! } ?: listOf()
      service.addToDownstream(dependencies)
    }
  }

  // Checks that no service in this builder has specified a dependency cycle.
  private fun checkCycles() {
    val validityMap =
        mutableMapOf<CoordinatedService2, CoordinatedService2.Companion.CycleValidity>()

    for ((_, service) in serviceMap) {
      val cycle = service.findCycle(validityMap)
      if (cycle != null) {
        throw IllegalStateException("Detected cycle: ${cycle.joinToString(" -> ")}")
      }
    }
  }

  // Checks that each service registered with this builder has its dependencies registered.
  // (i.e. no one service requires a dependency or enhancement that doesn't exist)
  private fun validateDependencyMap() {
    for ((big, dependents) in dependencyMap.asMap()) {
      if (serviceMap[big] == null) {
        val stringBuilder = StringBuilder()
        for (dependent in dependents) {
          stringBuilder.append("${serviceMap[dependent]}")
        }
        throw IllegalStateException("${stringBuilder.toString()} requires $big but no such service "
            + "was registered with the builder")
      }
    }
  }

  // Checks that no enhancement has been applied more than once.
  private fun validateEnhancementMap() {
    val enhancementList = mutableListOf<Key<*>>()
    for ((_, set) in enhancementMap.asMap()) {
      enhancementList.addAll(set)
    }
    enhancementList.groupingBy { it }.eachCount().forEach {
      check(it.value <= 1) { "Enhancement ${it.key} cannot be applied more than once" }
    }
  }

  private fun enhanceService(key: Key<*>) {
    val service = serviceMap[key]!!
    val enhancements = enhancementMap[key]?.map { serviceMap[it]!! } ?: listOf()
    service.enhanceWith(enhancements)
  }
}
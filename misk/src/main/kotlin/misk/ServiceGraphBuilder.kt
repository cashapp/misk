package misk

import com.google.common.collect.HashMultimap
import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.SetMultimap
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Key

// MultiMap -> Map of Sets

/**
 * Builds a graph of CoordinatedService2s which defer start up and shut down until their dependent
 * services are ready.
 */
class ServiceGraphBuilder {
  internal var serviceMap = mutableMapOf<Key<*>, CoordinatedService2>() // map of all services
  var dependencyMap = mutableMapOf<Key<*>, MutableSet<Key<*>>>()
  var enhancementMap = mutableMapOf<Key<*>, MutableSet<Key<*>>>()
  var dependencyGraph = mutableMapOf<Key<*>, MutableSet<Key<*>>>()

//  var dependencyMultiMap = HashMultimap.create<Key<*>, Key<*>>()
//  var enhancementMultimap = HashMultimap.create<Key<*>, Key<*>>()

  /**
   * Registers a service with this ServiceGraphBuilder.
   * A service must be added first before it can have dependencies or enhancements applied.
   * Keys must be unique. If a key is reused, then the original key-service pair will be replaced.
   */
  fun addService(key: Key<*>, service: Service) {
    serviceMap[key] = CoordinatedService2(service)
  }

  /**
   * Adds the (small) service as a dependency to the (big) provider Service.
   *
   * Small depends on big.
   */
  fun addDependency(small: Key<*>, big: Key<*>) {
    // maintain a set of dependents on the provider
    if (dependencyMap[big] == null) {
      dependencyMap[big] = mutableSetOf()
    }
    val dependencySet = dependencyMap[big]
    dependencySet!!.add(small)
  }

  /**
   * Adds a (small) enhancement to the (big) service. The (big) service depends on its enhancements.
   *
   * Service enhancements (small) will be started before the (big) service is started.
   * They will be stopped before the (big) service is done.
   */
  fun addEnhancement(big: Key<*>, small: Key<*>) {
    // maintain enhancements to be applied to services
    if (enhancementMap[big] == null) {
      enhancementMap[big] = mutableSetOf()
    }
    val enhancementSet = enhancementMap[big]
    enhancementSet!!.add(small)
  }

  /**
   * Builds a service manager.
   */
  fun build(): ServiceManager {
    linkDependencies()
    checkDependencies()
    return ServiceManager(serviceMap.values)
  }

  // builds coordinated services from the instructions provided in the dependency map
  private fun linkDependencies() {
    // for each service, add its dependencies and ensure no dependency cycles
    for ((key, service) in serviceMap) {
      // first get the enhancements for the service and handle their downstreams
      enhanceService(key)
      // now handle regular dependencies
      val dependencies = dependencyMap[key]?.map { serviceMap[it]!! } ?: listOf()

      service.addToDownstream(dependencies)
      service.requireNoCycles() // throws
    }
  }

  // check that each service has its dependencies met
  // (i.e. no one service requires a dependency that doesn't exist)
  private fun checkDependencies() {

  }

  private fun enhanceService(key: Key<*>) {
    val service = serviceMap[key]!!
    val enhancements = enhancementMap[key]?.map { serviceMap[it]!! } ?: listOf()
    // enhancements will have their service to be enhanced downstream
    enhancements.forEach { it.addToDownstream(listOf(service)) }
    // any dependents of the enhanced service must depend on enhancers
    service.downstream.forEach { it.addToDownstream(enhancements) }
  }
}
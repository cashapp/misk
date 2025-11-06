package misk

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.Provider
import com.google.inject.Provides
import com.google.inject.Scopes
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.test.assertFailsWith
import misk.inject.AsyncSwitch
import misk.inject.KAbstractModule
import misk.inject.Switch
import misk.inject.getInstance
import misk.inject.keyOf
import misk.inject.toKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

internal class ServiceManagerModuleTest {
  @Singleton
  class SingletonService1 : AbstractIdleService() {
    override fun startUp() {}

    override fun shutDown() {}
  }

  @jakarta.inject.Singleton
  class SingletonService2 : AbstractIdleService() {
    override fun startUp() {}

    override fun shutDown() {}
  }

  @javax.inject.Singleton
  class SingletonService3 : AbstractIdleService() {
    override fun startUp() {}

    override fun shutDown() {}
  }

  class NonSingletonService1 : AbstractIdleService() {
    override fun startUp() {}

    override fun shutDown() {}
  }

  class NonSingletonService2 : AbstractIdleService() {
    override fun startUp() {}

    override fun shutDown() {}
  }

  class ExplicitEagerSingletonService : AbstractIdleService() {
    override fun startUp() {}

    override fun shutDown() {}
  }

  class SingletonScopeService : AbstractIdleService() {
    override fun startUp() {}

    override fun shutDown() {}
  }

  class SingletonAnnotationService : AbstractIdleService() {
    override fun startUp() {}

    override fun shutDown() {}
  }

  class GoogleSingletonAnnotationService : AbstractIdleService() {
    override fun startUp() {}

    override fun shutDown() {}
  }

  class InstanceService : AbstractIdleService() {
    override fun startUp() {}

    override fun shutDown() {}
  }

  class ProvidesMethodService : AbstractIdleService() {
    override fun startUp() {}

    override fun shutDown() {}
  }

  @Test
  fun multibindingServicesThrows() {
    assertThat(
        assertFailsWith<com.google.inject.ProvisionException> {
            val injector =
              Guice.createInjector(
                MiskTestingServiceModule(),
                object : KAbstractModule() {
                  override fun configure() {
                    // Multibinding services is not supported. Expect that we throw for the first service
                    // that was bound.
                    multibind<Service>().to<SingletonService1>()
                    multibind<Service>().to<SingletonService2>()
                    multibind<Service>().to<SingletonService3>()
                  }
                },
              )

            injector.getInstance<ServiceManager>()
          }
          .message
      )
      .contains(
        "This doesn't work anymore! " +
          "Instead of using `multibind<Service>().to(SingletonService1)`, " +
          "use `install(ServiceModule<SingletonService1>())`."
      )
  }

  inline fun <reified S : Switch, reified O : Any, reified T : Any, reified E : T, reified D : T>ConditionalProvider(
    id: String,
    noinline transformer: (T) -> O = { it as O },
    ) = ConditionalProvider(id, S::class, O::class, T::class, E::class, D::class, transformer)

  class ConditionalProvider<S : Switch, O : Any, T : Any, E : T, D : T>(
    val id: String,
    val switchType: KClass<out S>, val outputType: KClass<out O>, val type: KClass<out T>, val enabledType: KClass<out E>, val disabledType: KClass<out D>,
    val transformer: (T) -> O = { it as O },
  ) : Provider<O> {
    @Inject lateinit var switch: S
    @Inject lateinit var enabled: E
    @Inject lateinit var disabled: D

    override fun get(): O? {
      return if (switch.isEnabled(id)) {
        transformer(enabled)
      } else {
        transformer(disabled)
      }
    }
  }

  class NoOpService : AbstractIdleService() {
    override fun startUp() {}

    override fun shutDown() {}
  }

  inline fun <reified S : Switch, reified O : Any, reified T : Any, reified E : T, reified D : T>ConditionalServiceModule(
    id: String,
    noinline transformer: (T) -> O = { it as O },
  ) = ConditionalProvider(id, S::class, O::class, T::class, E::class, D::class, transformer)


  class ConditionalServiceModule(
    val id: String,
    val switch: KClass<out Switch>,
    val key: Key<out Service>,
    val disabledKey: Key<out Service>,
    val dependsOn: List<Key<out Service>> = listOf(),
    val enhancedBy: List<Key<out Service>> = listOf(),
  ) : KAbstractModule() {
    override fun configure() {
//      multibind<ServiceEntry>()
//        .toProvider(
//          ConditionalProvider<AsyncSwitch, ServiceEntry, Service, SingletonService1, NoOpService>(
//            "sqs",
//          ) {
//            ServiceEntry(it::class.toKey())
//          }
//        )

      multibind<ServiceEntry>()
        .toProvider(
          ConditionalProvider(
            id,
            switch, ServiceEntry::class, Key::class, key.typeLiteral.javaClass.kotlin, disabledKey.typeLiteral.javaClass.kotlin,
          ) {
            ServiceEntry(it as Key<out Service>)
          }
        )

      for (dependsOnKey in dependsOn) {
        multibind<DependencyEdge>().toInstance(DependencyEdge(dependent = key, dependsOn = dependsOnKey))
      }
      for (enhancedByKey in enhancedBy) {
        multibind<EnhancementEdge>().toInstance(EnhancementEdge(toBeEnhanced = key, enhancement = enhancedByKey))
      }
    }
  }

  @Test
  fun detectsNonSingletonServiceEntries() {
    assertThat(
        assertFailsWith<com.google.inject.ProvisionException> {
            val injector =
              Guice.createInjector(
                MiskTestingServiceModule(),
                object : KAbstractModule() {
                  override fun configure() {
                    // Should be recognized as singletons
                    multibind<ServiceEntry>().toProvider(ConditionalProvider<AsyncSwitch>("sqs", Service::class, Sqs))

                    install(ServiceModule<SingletonService1>())
                    install(ServiceModule<SingletonService2>())
                    install(ServiceModule<SingletonService3>())
                    install(ServiceModule<ProvidesMethodService>())
                    install(ServiceModule<InstanceService>())
                    bind(keyOf<InstanceService>()).toInstance(InstanceService())
                    install(ServiceModule<ExplicitEagerSingletonService>())
                    bind(keyOf<ExplicitEagerSingletonService>()).asEagerSingleton()
                    install(ServiceModule<SingletonScopeService>())
                    bind(keyOf<SingletonScopeService>()).`in`(Scopes.SINGLETON)
                    install(ServiceModule<SingletonAnnotationService>())
                    bind(keyOf<SingletonAnnotationService>()).`in`(Singleton::class.java)
                    install(ServiceModule<GoogleSingletonAnnotationService>())
                    bind(keyOf<GoogleSingletonAnnotationService>()).`in`(Singleton::class.java)

                    // Should be recognized as non-singletons
                    install(ServiceModule<NonSingletonService1>())
                    install(ServiceModule<NonSingletonService2>())
                  }

                  @Provides @Singleton fun providesSingletonService(): ProvidesMethodService = ProvidesMethodService()
                },
              )

            injector.getInstance<ServiceManager>()
          }
          .message
      )
      .contains(
        "the following services are not marked as @Singleton: " +
          "misk.ServiceManagerModuleTest\$NonSingletonService1, " +
          "misk.ServiceManagerModuleTest\$NonSingletonService2"
      )
  }

  @Singleton
  class ProducerService @Inject constructor(private val log: StringBuilder) : AbstractService() {
    init {
      log.append("ProducerService.init\n")
    }

    override fun doStart() {
      log.append("ProducerService.startUp\n")
      notifyStarted()
    }

    override fun doStop() {
      log.append("ProducerService.shutDown\n")
      notifyStopped()
    }
  }

  @Singleton
  class ConsumerService @Inject constructor(private val log: StringBuilder) : AbstractService() {
    init {
      log.append("ConsumerService.init\n")
    }

    override fun doStart() {
      log.append("ConsumerService.startUp\n")
      notifyStarted()
    }

    override fun doStop() {
      log.append("ConsumerService.shutDown\n")
      notifyStopped()
    }
  }

  @Singleton
  class AnotherUpstreamService @Inject constructor(private val log: StringBuilder) : AbstractService() {
    init {
      log.append("AnotherUpstreamService.init\n")
    }

    override fun doStart() {
      log.append("AnotherUpstreamService.startUp\n")
      notifyStarted()
    }

    override fun doStop() {
      log.append("AnotherUpstreamService.shutDown\n")
      notifyStopped()
    }
  }

  @Test
  fun serviceNotProvidedUntilAllDependenciesCreated() {
    val log = StringBuilder()
    val injector =
      Guice.createInjector(
        MiskTestingServiceModule(),
        object : KAbstractModule() {
          override fun configure() {
            bind<StringBuilder>().toInstance(log)
            install(ServiceModule<ProducerService>())
            install(ServiceModule<ConsumerService>().dependsOn<ProducerService>().dependsOn<AnotherUpstreamService>())
            install(ServiceModule<AnotherUpstreamService>())
          }
        },
      )

    val serviceManager = injector.getInstance<ServiceManager>()
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    log.append("healthy\n")
    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    assertThat(log.toString())
      .isEqualTo(
        """
        |ProducerService.init
        |ProducerService.startUp
        |AnotherUpstreamService.init
        |AnotherUpstreamService.startUp
        |ConsumerService.init
        |ConsumerService.startUp
        |healthy
        |ConsumerService.shutDown
        |ProducerService.shutDown
        |AnotherUpstreamService.shutDown
        |"""
          .trimMargin()
      )
  }
}

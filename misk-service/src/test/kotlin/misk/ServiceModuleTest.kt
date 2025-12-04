package misk

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.inject.AlwaysEnabledSwitch
import misk.inject.AsyncSwitch
import misk.inject.DefaultAsyncSwitchModule
import misk.inject.KAbstractModule
import misk.inject.Switch
import misk.inject.getInstance
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ServiceModuleTest {
  class TestSwitch(var enabled: Boolean = true) : Switch {
    override fun isEnabled(key: String): Boolean = enabled
  }

  class KeyAwareSwitch(private val enabledKeys: Set<String> = emptySet()) : Switch {
    override fun isEnabled(key: String): Boolean = enabledKeys.contains(key)
  }

  @Singleton
  class TestService @Inject constructor(private val log: StringBuilder) : AbstractIdleService() {
    override fun startUp() { log.append("TestService.startUp\n") }
    override fun shutDown() { log.append("TestService.shutDown\n") }
  }

  @Singleton
  class UpstreamService @Inject constructor(private val log: StringBuilder) : AbstractIdleService() {
    override fun startUp() { log.append("UpstreamService.startUp\n") }
    override fun shutDown() { log.append("UpstreamService.shutDown\n") }
  }

  @Singleton
  class EnhancementService @Inject constructor(private val log: StringBuilder) : AbstractIdleService() {
    override fun startUp() { log.append("EnhancementService.startUp\n") }
    override fun shutDown() { log.append("EnhancementService.shutDown\n") }
  }

  @Test
  fun conditionalOn_whenEnabled_bindsRealService() {
    val enabledSwitch = TestSwitch(enabled = true)
    val log = StringBuilder()
    val injector = Guice.createInjector(
      MiskTestingServiceModule(),
      object : KAbstractModule() {
        override fun configure() {
          bind<StringBuilder>().toInstance(log)
          bind<TestSwitch>().toInstance(enabledSwitch)
          install(ServiceModule<TestService>().conditionalOn<TestSwitch>("test"))
        }
      }
    )

    val serviceManager = injector.getInstance<ServiceManager>()
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    assertThat(log.toString()).contains("TestService.startUp")
    assertThat(log.toString()).contains("TestService.shutDown")
  }

  @Test
  fun conditionalOn_whenDisabled_bindsNoOpService() {
    val disabledSwitch = TestSwitch(enabled = false)
    val log = StringBuilder()
    val injector = Guice.createInjector(
      MiskTestingServiceModule(),
      object : KAbstractModule() {
        override fun configure() {
          bind<StringBuilder>().toInstance(log)
          bind<TestSwitch>().toInstance(disabledSwitch)
          install(ServiceModule<TestService>().conditionalOn<TestSwitch>("test"))
        }
      }
    )

    val serviceManager = injector.getInstance<ServiceManager>()
    val services = serviceManager.servicesByState().values().map { it.javaClass.simpleName }
    
    assertThat(services).doesNotContain(TestService::class.java.simpleName)
    
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    serviceManager.stopAsync()
    serviceManager.awaitStopped()
    
    assertThat(log.toString()).doesNotContain("TestService.startUp")
    assertThat(log.toString()).doesNotContain("TestService.shutDown")
  }

  @Test
  fun conditionalOn_withAsyncSwitch_bindsRealServiceWhenEnabled() {
    val log = StringBuilder()
    val injector = Guice.createInjector(
      MiskTestingServiceModule(),
      object : KAbstractModule() {
        override fun configure() {
          bind<StringBuilder>().toInstance(log)
          install(DefaultAsyncSwitchModule())
          install(ServiceModule<TestService>().conditionalOn<AsyncSwitch>("test"))
        }
      }
    )

    val asyncSwitch = injector.getInstance<AsyncSwitch>()
    assertTrue(asyncSwitch is AlwaysEnabledSwitch)

    val serviceManager = injector.getInstance<ServiceManager>()
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    assertThat(log.toString()).contains("TestService.startUp")
    assertThat(log.toString()).contains("TestService.shutDown")
  }

  @Test
  fun conditionalOn_withDependencies_whenEnabled_bindsRealServiceWithDependencies() {
    val enabledSwitch = TestSwitch(enabled = true)
    val log = StringBuilder()
    val injector = Guice.createInjector(
      MiskTestingServiceModule(),
      object : KAbstractModule() {
        override fun configure() {
          bind<StringBuilder>().toInstance(log)
          bind<TestSwitch>().toInstance(enabledSwitch)
          install(ServiceModule<UpstreamService>())
          install(ServiceModule<TestService>()
            .dependsOn<UpstreamService>()
            .conditionalOn<TestSwitch>("test"))
        }
      }
    )

    val serviceManager = injector.getInstance<ServiceManager>()
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    log.append("healthy\n")
    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    assertThat(log.toString()).contains("UpstreamService.startUp")
    assertThat(log.toString()).contains("TestService.startUp")
  }

  @Test
  fun conditionalOn_withDependencies_whenDisabled_bindsNoOpServiceWithNoDependencies() {
    val disabledSwitch = TestSwitch(enabled = false)
    val log = StringBuilder()
    val injector = Guice.createInjector(
      MiskTestingServiceModule(),
      object : KAbstractModule() {
        override fun configure() {
          bind<StringBuilder>().toInstance(log)
          bind<TestSwitch>().toInstance(disabledSwitch)
          install(ServiceModule<UpstreamService>())
          install(ServiceModule<TestService>()
            .dependsOn<UpstreamService>()
            .conditionalOn<TestSwitch>("test"))
        }
      }
    )

    val serviceManager = injector.getInstance<ServiceManager>()
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    serviceManager.stopAsync()
    serviceManager.awaitStopped()
    
    assertThat(log.toString()).contains("UpstreamService.startUp")
    assertThat(log.toString()).doesNotContain("TestService.startUp")
  }

  @Test
  fun conditionalOn_withEnhancements_whenEnabled_bindsRealServiceWithEnhancements() {
    val enabledSwitch = TestSwitch(enabled = true)
    val log = StringBuilder()
    val injector = Guice.createInjector(
      MiskTestingServiceModule(),
      object : KAbstractModule() {
        override fun configure() {
          bind<StringBuilder>().toInstance(log)
          bind<TestSwitch>().toInstance(enabledSwitch)
          install(ServiceModule<EnhancementService>())
          install(ServiceModule<TestService>()
            .enhancedBy<EnhancementService>()
            .conditionalOn<TestSwitch>("test"))
        }
      }
    )

    val serviceManager = injector.getInstance<ServiceManager>()
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    serviceManager.stopAsync()
    serviceManager.awaitStopped()
    
    assertThat(log.toString()).contains("TestService.startUp")
    assertThat(log.toString()).contains("EnhancementService.startUp")
  }

  @Test
  fun conditionalOn_withEnhancements_whenDisabled_bindsNoOpServiceWithNoEnhancements() {
    val disabledSwitch = TestSwitch(enabled = false)
    val log = StringBuilder()
    val injector = Guice.createInjector(
      MiskTestingServiceModule(),
      object : KAbstractModule() {
        override fun configure() {
          bind<StringBuilder>().toInstance(log)
          bind<TestSwitch>().toInstance(disabledSwitch)
          install(ServiceModule<EnhancementService>())
          install(ServiceModule<TestService>()
            .enhancedBy<EnhancementService>()
            .conditionalOn<TestSwitch>("test"))
        }
      }
    )

    val serviceManager = injector.getInstance<ServiceManager>()
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    serviceManager.stopAsync()
    serviceManager.awaitStopped()
    
    assertThat(log.toString()).doesNotContain("TestService.startUp")
    assertThat(log.toString()).contains("EnhancementService.startUp")
  }

  @Test
  fun conditionalOn_withDifferentSwitchKeys_respectsIndividualKeys() {
    val keyAwareSwitch = KeyAwareSwitch(enabledKeys = setOf("enabled-key"))
    val log = StringBuilder()
    val injector = Guice.createInjector(
      MiskTestingServiceModule(),
      object : KAbstractModule() {
        override fun configure() {
          bind<StringBuilder>().toInstance(log)
          bind<KeyAwareSwitch>().toInstance(keyAwareSwitch)
          install(ServiceModule<TestService>().conditionalOn<KeyAwareSwitch>("enabled-key"))
          install(ServiceModule<UpstreamService>().conditionalOn<KeyAwareSwitch>("disabled-key"))
        }
      }
    )

    val serviceManager = injector.getInstance<ServiceManager>()
    
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    serviceManager.stopAsync()
    serviceManager.awaitStopped()
    
    assertThat(log.toString()).contains("TestService.startUp")
    assertThat(log.toString()).doesNotContain("UpstreamService.startUp")
  }

  @Test
  fun multipleServicesWithConditionalOnDisabled_noGuiceErrors() {
    val disabledSwitch = TestSwitch(enabled = false)
    val log = StringBuilder()
    val injector = Guice.createInjector(
      MiskTestingServiceModule(),
      object : KAbstractModule() {
        override fun configure() {
          bind<StringBuilder>().toInstance(log)
          bind<TestSwitch>().toInstance(disabledSwitch)
          install(ServiceModule<TestService>().conditionalOn<TestSwitch>("test"))
          install(ServiceModule<UpstreamService>().conditionalOn<TestSwitch>("test"))
          install(ServiceModule<EnhancementService>().conditionalOn<TestSwitch>("test"))
        }
      }
    )

    val serviceManager = injector.getInstance<ServiceManager>()
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    serviceManager.stopAsync()
    serviceManager.awaitStopped()
    
    assertThat(log.toString()).doesNotContain("TestService.startUp")
    assertThat(log.toString()).doesNotContain("UpstreamService.startUp")
    assertThat(log.toString()).doesNotContain("EnhancementService.startUp")
  }

  @Test
  fun multipleServicesWithDependenciesAndEnhancementsAllDisabled_noGuiceErrors() {
    val disabledSwitch = TestSwitch(enabled = false)
    val log = StringBuilder()
    val injector = Guice.createInjector(
      MiskTestingServiceModule(),
      object : KAbstractModule() {
        override fun configure() {
          bind<StringBuilder>().toInstance(log)
          bind<TestSwitch>().toInstance(disabledSwitch)
          install(ServiceModule<UpstreamService>().conditionalOn<TestSwitch>("test"))
          install(ServiceModule<EnhancementService>().conditionalOn<TestSwitch>("test"))
          install(ServiceModule<TestService>()
            .dependsOn<UpstreamService>()
            .enhancedBy<EnhancementService>()
            .conditionalOn<TestSwitch>("test"))
        }
      }
    )

    val serviceManager = injector.getInstance<ServiceManager>()
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    serviceManager.stopAsync()
    serviceManager.awaitStopped()
    
    assertThat(log.toString()).doesNotContain("TestService.startUp")
    assertThat(log.toString()).doesNotContain("UpstreamService.startUp")
    assertThat(log.toString()).doesNotContain("EnhancementService.startUp")
  }

  @Test
  fun multipleServicesWithSameSwitchKey_someEnabledSomeDisabled_noGuiceErrors() {
    val keyAwareSwitch = KeyAwareSwitch(enabledKeys = setOf("enabled"))
    val log = StringBuilder()
    val injector = Guice.createInjector(
      MiskTestingServiceModule(),
      object : KAbstractModule() {
        override fun configure() {
          bind<StringBuilder>().toInstance(log)
          bind<KeyAwareSwitch>().toInstance(keyAwareSwitch)
          install(ServiceModule<TestService>().conditionalOn<KeyAwareSwitch>("enabled"))
          install(ServiceModule<UpstreamService>().conditionalOn<KeyAwareSwitch>("disabled"))
          install(ServiceModule<EnhancementService>().conditionalOn<KeyAwareSwitch>("disabled"))
        }
      }
    )

    val serviceManager = injector.getInstance<ServiceManager>()
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    serviceManager.stopAsync()
    serviceManager.awaitStopped()
    
    assertThat(log.toString()).contains("TestService.startUp")
    assertThat(log.toString()).doesNotContain("UpstreamService.startUp")
    assertThat(log.toString()).doesNotContain("EnhancementService.startUp")
  }

  @Test
  fun duplicateServiceModule_withIdenticalParameters_deduplicates() {
    // This would have failed before the fix with duplicate multibinding errors
    val enabledSwitch = TestSwitch(enabled = true)
    val log = StringBuilder()
    val injector = Guice.createInjector(
      MiskTestingServiceModule(),
      object : KAbstractModule() {
        override fun configure() {
          bind<StringBuilder>().toInstance(log)
          bind<TestSwitch>().toInstance(enabledSwitch)
          // Install the exact same ServiceModule twice
          install(ServiceModule<TestService>().conditionalOn<TestSwitch>("test"))
          install(ServiceModule<TestService>().conditionalOn<TestSwitch>("test"))
        }
      }
    )

    val serviceManager = injector.getInstance<ServiceManager>()
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    // Should only start once, not twice
    assertThat(log.toString()).contains("TestService.startUp")
    assertThat(log.toString().split("TestService.startUp").size - 1).isEqualTo(1)
  }

  @Test
  fun duplicateConditionalService_installedFromMultipleModules_deduplicates() {
    // Simulates the Auditorium scenario where multiple AwsSqsJobHandlerModule instances
    // each install the same conditional service
    val enabledSwitch = TestSwitch(enabled = true)
    val log = StringBuilder()
    
    class ModuleA : KAbstractModule() {
      override fun configure() {
        install(ServiceModule<TestService>().conditionalOn<TestSwitch>("shared"))
      }
    }
    
    class ModuleB : KAbstractModule() {
      override fun configure() {
        install(ServiceModule<TestService>().conditionalOn<TestSwitch>("shared"))
      }
    }
    
    val injector = Guice.createInjector(
      MiskTestingServiceModule(),
      object : KAbstractModule() {
        override fun configure() {
          bind<StringBuilder>().toInstance(log)
          bind<TestSwitch>().toInstance(enabledSwitch)
          install(ModuleA())
          install(ModuleB())
        }
      }
    )

    val serviceManager = injector.getInstance<ServiceManager>()
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    assertThat(log.toString()).contains("TestService.startUp")
    assertThat(log.toString().split("TestService.startUp").size - 1).isEqualTo(1)
  }

  @Test
  fun nestedConditionalService_fromMultipleLayers_deduplicates() {
    // Simulates the Orderly scenario where multiple publishing modules each install
    // a job handler module, which in turn installs the same conditional subscription service
    val enabledSwitch = TestSwitch(enabled = true)
    val log = StringBuilder()
    
    class JobHandlerModuleA : KAbstractModule() {
      override fun configure() {
        install(ServiceModule<UpstreamService>().conditionalOn<TestSwitch>("worker"))
      }
    }
    
    class JobHandlerModuleB : KAbstractModule() {
      override fun configure() {
        install(ServiceModule<UpstreamService>().conditionalOn<TestSwitch>("worker"))
      }
    }
    
    class PublishingModuleA : KAbstractModule() {
      override fun configure() {
        install(JobHandlerModuleA())
      }
    }
    
    class PublishingModuleB : KAbstractModule() {
      override fun configure() {
        install(JobHandlerModuleB())
      }
    }
    
    val injector = Guice.createInjector(
      MiskTestingServiceModule(),
      object : KAbstractModule() {
        override fun configure() {
          bind<StringBuilder>().toInstance(log)
          bind<TestSwitch>().toInstance(enabledSwitch)
          install(PublishingModuleA())
          install(PublishingModuleB())
        }
      }
    )

    val serviceManager = injector.getInstance<ServiceManager>()
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    // Should only start once despite being installed from two different module hierarchies
    assertThat(log.toString()).contains("UpstreamService.startUp")
    assertThat(log.toString().split("UpstreamService.startUp").size - 1).isEqualTo(1)
  }

  @Test
  fun duplicateConditionalService_withDifferentSwitchKeys_doesNotDeduplicate() {
    val keyAwareSwitch = KeyAwareSwitch(enabledKeys = setOf("key1"))
    val log = StringBuilder()
    val injector = Guice.createInjector(
      MiskTestingServiceModule(),
      object : KAbstractModule() {
        override fun configure() {
          bind<StringBuilder>().toInstance(log)
          bind<KeyAwareSwitch>().toInstance(keyAwareSwitch)
          // Different switchKeys should NOT deduplicate - they are different bindings
          install(ServiceModule<TestService>().conditionalOn<KeyAwareSwitch>("key1"))
          install(ServiceModule<UpstreamService>().conditionalOn<KeyAwareSwitch>("key2"))
        }
      }
    )

    val serviceManager = injector.getInstance<ServiceManager>()
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    // Only key1 is enabled, so only TestService starts
    assertThat(log.toString()).contains("TestService.startUp")
    assertThat(log.toString()).doesNotContain("UpstreamService.startUp")
  }

  @Test
  fun duplicateConditionalService_withDifferentDependencies_doesNotDeduplicate() {
    val enabledSwitch = TestSwitch(enabled = true)
    val log = StringBuilder()
    val injector = Guice.createInjector(
      MiskTestingServiceModule(),
      object : KAbstractModule() {
        override fun configure() {
          bind<StringBuilder>().toInstance(log)
          bind<TestSwitch>().toInstance(enabledSwitch)
          install(ServiceModule<UpstreamService>())
          // Different dependencies should NOT deduplicate - they are different configurations
          install(ServiceModule<TestService>().conditionalOn<TestSwitch>("test"))
          install(ServiceModule<EnhancementService>()
            .dependsOn<UpstreamService>()
            .conditionalOn<TestSwitch>("test"))
        }
      }
    )

    val serviceManager = injector.getInstance<ServiceManager>()
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    assertThat(log.toString()).contains("TestService.startUp")
    assertThat(log.toString()).contains("EnhancementService.startUp")
    assertThat(log.toString()).contains("UpstreamService.startUp")
  }

  @Test
  fun unconditionalServiceModule_duplicateInstalls_deduplicates() {
    val log = StringBuilder()
    val injector = Guice.createInjector(
      MiskTestingServiceModule(),
      object : KAbstractModule() {
        override fun configure() {
          bind<StringBuilder>().toInstance(log)
          // Install the same unconditional service twice
          install(ServiceModule<TestService>())
          install(ServiceModule<TestService>())
        }
      }
    )

    val serviceManager = injector.getInstance<ServiceManager>()
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    // Should only start once
    assertThat(log.toString()).contains("TestService.startUp")
    assertThat(log.toString().split("TestService.startUp").size - 1).isEqualTo(1)
  }
}
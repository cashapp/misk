package misk.embedded

import misk.embedded.sample.embedded.EmbeddedSampleService
import misk.embedded.sample.embedded.SampleEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EmbeddedMiskTest {
  @Test
  fun `embed the same service multiple times`() {
    val sampleService1 = EmbeddedMisk.create<EmbeddedSampleService>()
    assertThat(sampleService1.staticCounter.getAndIncrement()).isEqualTo(0)
    assertThat(sampleService1.staticCounter.getAndIncrement()).isEqualTo(1)

    val sampleService2 = EmbeddedMisk.create<EmbeddedSampleService>()
    assertThat(sampleService2.staticCounter.getAndIncrement()).isEqualTo(0)
    assertThat(sampleService2.staticCounter.getAndIncrement()).isEqualTo(1)
  }

  @Test
  fun `local and embedded versions dont collide on classpath`() {
    val sampleService = EmbeddedMisk.create<EmbeddedSampleService>()
    sampleService.serviceManager.startAsync()
    sampleService.serviceManager.awaitHealthy()
    sampleService.consumeEvent(SampleEvent("hello 1"))
    sampleService.consumeEvent(SampleEvent("hello 2"))

    // This successfully loads a SampleHibernate brought in as a transitive dependency of
    // RealEmbeddedSampleService.
    assertThat(sampleService.loadPersistedValues()).containsExactly(
        "Hibernate5:hello 1",
        "Hibernate5:hello 2"
    )

    val localService = LocalService()
    localService.consumeEvent(SampleEvent("hello 1"))
    localService.consumeEvent(SampleEvent("hello 2"))

    // This successfully loads the local version of SampleHibernate.
    assertThat(localService.loadHibernateValues()).containsExactly(
        "Hibernate4:hello 1",
        "Hibernate4:hello 2"
    )
  }

  class LocalService {
    private val sampleHibernate = SampleHibernate()
    fun consumeEvent(event: SampleEvent) {
      sampleHibernate.save(event.data)
    }

    fun loadHibernateValues() = sampleHibernate.loadAll()

    class SampleHibernate {
      private val values = mutableListOf<String>()

      fun save(value: String) {
        values += "$hibernateVersion:$value"
      }

      fun loadAll() = values.toList()
      companion object {
        private const val hibernateVersion = "Hibernate4"
      }
    }
  }
}

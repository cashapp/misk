package misk.embedded.sample.embedded

import com.google.common.util.concurrent.ServiceManager
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealEmbeddedSampleService @Inject constructor(
  override val serviceManager: ServiceManager,
  private val hibernate: SampleHibernate
) : EmbeddedSampleService {
  override val staticCounter = globalCounter

  override fun consumeEvent(event: SampleEvent) {
    hibernate.save(event.data)
  }

  override fun loadPersistedValues() = hibernate.loadAll()

  companion object {
    val globalCounter = AtomicInteger()
  }

  /** Local to the service submodule. */
  @Singleton
  class SampleHibernate @Inject constructor() {
    private val values = mutableListOf<String>()

    fun save(value: String) {
      values += "$hibernateVersion:$value"
    }

    fun loadAll() = values.toList()

    companion object {
      const val hibernateVersion = "Hibernate5"
    }
  }
}

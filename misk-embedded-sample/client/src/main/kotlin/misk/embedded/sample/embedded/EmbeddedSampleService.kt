package misk.embedded.sample.embedded

import com.google.common.util.concurrent.ServiceManager
import java.util.concurrent.atomic.AtomicInteger

/** Sample service that consumes [SampleEvent]s and persists them locally. */
interface EmbeddedSampleService {
  val serviceManager: ServiceManager
  val staticCounter: AtomicInteger
  fun consumeEvent(event: SampleEvent)

  fun loadPersistedValues(): List<String>
}

data class SampleEvent(val data: String)

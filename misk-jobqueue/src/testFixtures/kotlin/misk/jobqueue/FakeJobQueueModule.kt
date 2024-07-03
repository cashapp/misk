package misk.jobqueue

import misk.inject.KAbstractModule
import misk.web.metadata.MetadataModule

class FakeJobQueueModule : KAbstractModule() {
  override fun configure() {
    bind<JobQueue>().to<FakeJobQueue>()
    bind<TransactionalJobQueue>().to<FakeJobQueue>()

    install(JobqueueMetadataModule())
  }
}

package misk.jobqueue

import misk.inject.KAbstractModule

@Deprecated("Replace the dependency on misk-jobqueue-testing with testFixtures(misk-jobqueue)")
class FakeJobQueueModule : KAbstractModule() {
  override fun configure() {
    bind<JobQueue>().to<FakeJobQueue>()
    bind<TransactionalJobQueue>().to<FakeJobQueue>()
  }
}

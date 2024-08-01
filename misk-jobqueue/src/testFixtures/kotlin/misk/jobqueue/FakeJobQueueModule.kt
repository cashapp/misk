package misk.jobqueue

import misk.inject.KAbstractModule
import misk.testing.TestFixture

class FakeJobQueueModule : KAbstractModule() {
  override fun configure() {
    bind<JobQueue>().to<FakeJobQueue>()
    bind<TransactionalJobQueue>().to<FakeJobQueue>()
    multibind<TestFixture>().to<FakeJobQueue>()
  }
}

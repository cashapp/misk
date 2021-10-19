package misk.jobqueue

import misk.inject.KAbstractModule

class FakeJobQueueModule : KAbstractModule() {
  override fun configure() {
    bind<JobQueue>().to<FakeJobQueue>()
  }
}

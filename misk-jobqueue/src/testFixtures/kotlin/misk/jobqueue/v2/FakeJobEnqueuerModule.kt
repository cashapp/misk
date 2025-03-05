package misk.jobqueue.v2

import misk.inject.KAbstractModule
import misk.testing.TestFixture

class FakeJobEnqueuerModule: KAbstractModule() {
  override fun configure() {
    bind<JobEnqueuer>().to<FakeJobEnqueuer>()
    multibind<TestFixture>().to<FakeJobEnqueuer>()
  }
}

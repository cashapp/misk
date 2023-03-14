package com.squareup.exemplar

import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.time.FakeClockModule
import misk.tokens.FakeTokenGeneratorModule
import wisp.deployment.TESTING

class ExemplarTestModule : KAbstractModule() {
  override fun configure() {
    install(DeploymentModule(TESTING))
    install(FakeClockModule())
    install(FakeTokenGeneratorModule())
  }
}

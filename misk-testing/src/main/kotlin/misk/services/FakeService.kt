package misk.services

import com.google.common.util.concurrent.AbstractIdleService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeService @Inject constructor() : AbstractIdleService() {
  override fun startUp() {
  }

  override fun shutDown() {
  }
}

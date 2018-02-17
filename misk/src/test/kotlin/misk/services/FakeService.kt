package misk.services

import com.google.common.util.concurrent.AbstractIdleService
import javax.inject.Singleton

@Singleton
class FakeService : AbstractIdleService() {
  override fun startUp() {
  }

  override fun shutDown() {
  }
}

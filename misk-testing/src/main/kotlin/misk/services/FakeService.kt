package misk.services

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Inject
import com.google.inject.Singleton

@Singleton
class FakeService @Inject constructor() : AbstractIdleService() {
  override fun startUp() {
  }

  override fun shutDown() {
  }
}

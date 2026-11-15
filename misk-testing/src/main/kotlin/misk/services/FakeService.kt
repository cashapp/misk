package misk.services

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class FakeService @Inject constructor() : AbstractIdleService() {
  override fun startUp() {}

  override fun shutDown() {}
}

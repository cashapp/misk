package misk.reset

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import com.google.inject.Inject
import com.google.inject.Key
import com.google.inject.Singleton
import misk.DependentService

@Singleton
class ResetCoordinationService @Inject internal constructor(
  @RunResetService val runResetService: Boolean,
  @ResetService val resetServices: List<Service>
) : AbstractIdleService(), DependentService {

  val devModeServiceKeys = resetServices.map { Key.get(it::class.java).typeLiteral }

  override val consumedKeys: Set<Key<*>> =
      resetServices.fold(setOf()) { consumedKeysSet, service ->
        consumedKeysSet + (service as DependentService).consumedKeys.filter {
          // TODO(nb): don't filter out keys with matching type but different annotations
          !devModeServiceKeys.contains(it.typeLiteral)
        }
      }

  override val producedKeys: Set<Key<*>> = setOf(Key.get(ResetCoordinationService::class.java))

  override fun startUp() {
    if (runResetService) {
      resetServices.forEach {
        it.startAsync()
        it.awaitRunning()
      }
    }
  }

  override fun shutDown() {
    if (runResetService) {
      resetServices.forEach { it.stopAsync() }
    }
  }
}

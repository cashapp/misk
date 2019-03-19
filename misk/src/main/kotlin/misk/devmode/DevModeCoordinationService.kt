package misk.devmode

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import com.google.inject.Inject
import com.google.inject.Key
import com.google.inject.Singleton
import misk.DependentService
import misk.inject.toKey

@Singleton
class DevModeCoordinationService @Inject internal constructor(
  @DevMode val isDevMode: Boolean,
  @DevModeService val devModeServices: List<Service>
) : AbstractIdleService(), DependentService {

  val devModeServiceKeys = devModeServices.map { Key.get(it::class.java).typeLiteral }

  override val consumedKeys: Set<Key<*>> =
      devModeServices.fold(setOf()) { consumedKeysSet, service ->
        consumedKeysSet + (service as DependentService).consumedKeys.filter {
          // TODO(nb): don't filter out keys with matching type but different annotations
          !devModeServiceKeys.contains(it.typeLiteral)
        }
      }

  override val producedKeys: Set<Key<*>> = setOf(Key.get(DevModeCoordinationService::class.java))

  override fun startUp() {
    if (isDevMode) {
      devModeServices.forEach {
        it.startAsync()
        it.awaitRunning()
      }
    }
  }

  override fun shutDown() {
    if (isDevMode) {
      devModeServices.forEach { it.stopAsync() }
    }
  }
}

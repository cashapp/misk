package misk.warmup

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Key
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import misk.inject.toKey
import kotlin.reflect.KClass

class WarmupModule constructor(
  /** A `snake_case` task name suitable for logs and metrics. */
  private val name: String,
  private val key: Key<out WarmupTask>
) : KAbstractModule() {
  override fun configure() {
    newMapBinder<String, WarmupTask>().addBinding(name).to(key)
    install(CommonModule)
  }

  /** Common dependencies here so Guice can deduplicate installs of this module. */
  private object CommonModule : KAbstractModule() {
    override fun configure() {
      multibind<ServiceManager.Listener>().to<WarmupRunner>()
      multibind<HealthCheck>().to<WarmupRunner>()
    }
  }
}

inline fun <reified T : WarmupTask> WarmupModule(qualifier: KClass<out Annotation>? = null) =
  WarmupModule(T::class.simpleName!!, T::class.toKey(qualifier))

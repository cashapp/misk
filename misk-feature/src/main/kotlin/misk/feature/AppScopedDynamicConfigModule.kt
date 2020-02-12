package misk.feature

import com.google.inject.TypeLiteral
import misk.config.AppName
import misk.inject.KAbstractModule
import misk.inject.parameterizedType
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

/**
 * A module that binds a single instance of [AppScopedDynamicConfigResolver] for the given type,
 * registering the name suffix (following appName-) to be used to retrieve the backing feature.
 * Registration also involves the specification of a failure mode default (failure modes including
 * invalid feature value specification that makes the output fail parsing).
 *
 * Use [AppScopedDynamicConfigModule.create] to register a config name with a config type.
 */
class AppScopedDynamicConfigModule<T : ValidatableConfig<T>> private constructor(
  private val configName: String,
  private val configType: KClass<T>,
  private val failureModeDefault: T
) : KAbstractModule() {

  override fun configure() {
    val type = (parameterizedType<AppScopedDynamicConfigResolver<T>>(configType.java))
    @Suppress("UNCHECKED_CAST")
    val typeLiteral = TypeLiteral.get(type) as TypeLiteral<AppScopedDynamicConfigResolver<T>>

    bind(typeLiteral)
        .toProvider(object : Provider<AppScopedDynamicConfigResolver<T>> {
          @Inject @AppName lateinit var appName: String
          @Inject lateinit var dynamicConfig: DynamicConfig

          override fun get() = RealAppScopedDynamicConfigResolver(appName, configName,
            dynamicConfig, failureModeDefault)
        })
        .asEagerSingleton()
  }

  companion object {
    fun <T : ValidatableConfig<T>> create(
      configName: String,
      configType: KClass<T>,
      failureModeDefault: T
    ): AppScopedDynamicConfigModule<T> {
      return AppScopedDynamicConfigModule(configName, configType, failureModeDefault)
    }
  }
}
package misk.config

import com.google.common.base.CaseFormat.LOWER_UNDERSCORE
import com.google.common.base.CaseFormat.UPPER_CAMEL
import com.google.inject.TypeLiteral
import com.google.inject.name.Names
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.typeLiteral
import javax.inject.Provider
import kotlin.reflect.KProperty1
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.jvm.javaType

class ConfigModule(
    private val configClass: Class<out Config>,
    private val appName: String
) : KAbstractModule() {
  @Suppress("UNCHECKED_CAST")
  override fun configure() {
    bind<String>().annotatedWith<AppName>()
        .toInstance(appName)
    bind(configClass).toProvider(ConfigProvider(configClass, appName))
        .asSingleton()
    bindConfigClassRecursively(configClass)
  }

  @Suppress("UNCHECKED_CAST")
  private fun bindConfigClassRecursively(configClass: Class<out Config>) {
    for (property in configClass.kotlin.declaredMemberProperties) {
      if (!property.returnType.isSubtypeOf(Config::class.createType())) {
        continue
      }
      bindConfigClassRecursively(
          property.returnType.typeLiteral().rawType as Class<out Config>
      )
      val subConfigProvider = SubConfigProvider(
          getProvider(configClass),
          property as KProperty1<Config, Any?>
      )
      val subConfigTypeLiteral = TypeLiteral.get(
          property.returnType.javaType
      ) as TypeLiteral<Any?>

      val subConfigTypeName = subConfigTypeLiteral.rawType.simpleName
      val defaultSubConfigFieldName = UPPER_CAMEL.to(LOWER_UNDERSCORE, subConfigTypeName)

      if (property.name == defaultSubConfigFieldName.substringBefore("_config")) {
        // The property is the name of the config type, without the unnecessary Config suffix.
        // This is the default binding for the config type, so bind it without requiring
        // a name annotation
        bind(subConfigTypeLiteral).toProvider(subConfigProvider)
            .asSingleton()
      } else {
        // The property differs from the name of the config type, so it is being explicitly
        // qualified.
        bind(subConfigTypeLiteral).annotatedWith(Names.named(property.name))
            .toProvider(subConfigProvider)
            .asSingleton()
      }
    }
  }

  internal class SubConfigProvider(
      private val configProvider: Provider<out Config>,
      private val subconfigGetter: KProperty1<Config, Any?>
  ) : Provider<Any?> {
    override fun get(): Any? {
      return subconfigGetter.get(configProvider.get())
    }
  }

  companion object {
    inline fun <reified T : Config> create(appName: String) =
        ConfigModule(T::class.java, appName)
  }
}

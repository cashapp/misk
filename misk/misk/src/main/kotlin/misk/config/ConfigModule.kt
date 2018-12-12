package misk.config

import com.google.common.base.CaseFormat.LOWER_UNDERSCORE
import com.google.common.base.CaseFormat.UPPER_CAMEL
import com.google.inject.TypeLiteral
import com.google.inject.name.Names
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import javax.inject.Provider
import kotlin.reflect.KProperty1
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.jvm.javaType

class ConfigModule<T : Config>(
  private val configClass: Class<T>,
  private val appName: String,
  private val config: T
) : KAbstractModule() {
  @Suppress("UNCHECKED_CAST")
  override fun configure() {
    bind<String>().annotatedWith<AppName>().toInstance(appName)
    bind(configClass).toInstance(config)
    bindChildConfigClass(configClass)
  }

  @Suppress("UNCHECKED_CAST")
  private fun bindChildConfigClass(configClass: Class<out Config>) {
    for (property in configClass.kotlin.declaredMemberProperties) {

      val configProperty = (property as KProperty1<Config, Any?>).get(config)

      val (subConfigReturnType, subConfigProvider) =
          if (property.returnType.isSubtypeOf(ANY_SECRET_TYPE)) {
            // We know that `property.returnType` is parameterized since it should be a Secret<T>.
            // actualSecretType == null if a STAR projection was used for the type.
            // If T is parameterized or STAR projection was used we cannot bind.
            val actualSecretType = property.returnType.arguments[0].type
            if (actualSecretType == null ||
                !actualSecretType.isSubtypeOf(Config::class.createType())) {
              continue
            }
            actualSecretType.javaType to Provider { (configProperty as Secret<T>).value }
          } else if (property.returnType.isSubtypeOf(Config::class.createType())) {
            property.returnType.javaType to Provider { configProperty }
          } else {
            continue
          }

      val subConfigTypeLiteral = TypeLiteral.get(subConfigReturnType) as TypeLiteral<Any?>
      val subConfigTypeName = subConfigTypeLiteral.rawType.simpleName
      val defaultSubConfigFieldName = UPPER_CAMEL.to(LOWER_UNDERSCORE, subConfigTypeName)

      if (property.name == defaultSubConfigFieldName.substringBefore("_config")) {
        // The property is the name of the config type, without the unnecessary Config suffix.
        // This is the default binding for the config type, so bind it without requiring
        // a name annotation
        bind(subConfigTypeLiteral).toProvider(subConfigProvider).asSingleton()
      } else {
        // The property differs from the name of the config type, so it is being explicitly
        // qualified.
        bind(subConfigTypeLiteral).annotatedWith(Names.named(property.name))
            .toProvider(subConfigProvider).asSingleton()
      }
    }
  }

  companion object {
    private val ANY_SECRET_TYPE = Secret::class.createType(listOf(KTypeProjection.STAR))
    inline fun <reified T : Config> create(appName: String, config: T) =
        ConfigModule(T::class.java, appName, config)
  }
}

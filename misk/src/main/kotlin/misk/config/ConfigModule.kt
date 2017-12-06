package misk.config

import com.google.inject.AbstractModule
import com.google.inject.TypeLiteral
import com.google.inject.name.Names
import misk.inject.asSingleton
import misk.web.typeLiteral
import javax.inject.Provider
import kotlin.reflect.KProperty1
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.jvm.javaType

class ConfigModule(
    private val configClass: Class<out Config>,
    private val appName: String
) : AbstractModule() {
    @Suppress("UNCHECKED_CAST")
    override fun configure() {
        bind(String::class.java).annotatedWith(AppName::class.java).toInstance(appName)
        bind(configClass).toProvider(ConfigProvider(configClass, appName)).asSingleton()
        bindConfigClassRecursively(configClass)
    }

    @Suppress("UNCHECKED_CAST")
    private fun bindConfigClassRecursively(configClass: Class<out Config>) {
        for (property in configClass.kotlin.declaredMemberProperties) {
            if (!property.returnType.isSubtypeOf(Config::class.createType())) {
                continue
            }
            bindConfigClassRecursively(property.returnType.typeLiteral.rawType as Class<out Config>)
            val subConfigProvider = SubConfigProvider(getProvider(configClass), property as KProperty1<Config, Any?>)
            val subConfigTypeLiteral = TypeLiteral.get(property.returnType.javaType) as TypeLiteral<Any?>

            if (subConfigTypeLiteral.rawType.simpleName.toLowerCase() == property.name.replace("_", "").toLowerCase()) {
                bind(subConfigTypeLiteral).toProvider(subConfigProvider).asSingleton()
            } else {
                bind(subConfigTypeLiteral).annotatedWith(Names.named(property.name)).toProvider(subConfigProvider).asSingleton()
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

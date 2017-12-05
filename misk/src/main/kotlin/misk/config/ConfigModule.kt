package misk.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.inject.AbstractModule
import com.google.inject.TypeLiteral
import com.google.inject.name.Names
import misk.inject.asSingleton
import misk.web.typeLiteral
import java.io.File
import java.nio.file.Files
import javax.inject.Provider
import kotlin.reflect.KProperty1
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.jvm.javaType

// TODO(jgulbronson) - Allow config to override. i.e. production.yaml overrides common.yaml
class ConfigModule(
    private val configClass: Class<out Config>,
    private val configFileName: String
) : AbstractModule() {
    @Suppress("UNCHECKED_CAST")
    override fun configure() {
        bind(configClass).toProvider(ConfigProvider(configClass, configFileName)).asSingleton()
        bindConfigClassRecursively(configClass)
    }

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

    internal class ConfigProvider<T : Config>(
        private val configClass: Class<out Config>,
        private val configFileName: String
    ) : Provider<T> {
        override fun get(): T {
            val mapper = ObjectMapper(YAMLFactory())
            mapper.registerModule(KotlinModule())

            // TODO(jgulbronson) - Infer resource file name from app name/environment
            val file = File(configClass.classLoader.getResource(configFileName).file)

            return Files.newBufferedReader(file.toPath()).use {
                @Suppress("UNCHECKED_CAST")
                mapper.readValue(it, configClass) as T
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
        inline fun <reified T : Config> create(configFileName: String) =
            ConfigModule(T::class.java, configFileName)
    }
}

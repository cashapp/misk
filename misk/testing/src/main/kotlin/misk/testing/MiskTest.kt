package misk.testing

import com.google.inject.Module
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@ExtendWith(MiskTestExtension::class)
/**
 * Annotate your test classes with `@MiskTest` to have fields annotated with `@Inject` initialized.
 * Provide zero or more [Module] classes or [Module] instances to be installed by annotating a
 * [ModuleProvider] field in your test class with [Modules].
 */
annotation class MiskTest

@Target(AnnotationTarget.FIELD)
annotation class Modules

data class ModuleProvider(val moduleList: List<Module> = ArrayList(), val moduleClassList: List<KClass<out Module>> = ArrayList()) {
    constructor(vararg modules: Module) : this(moduleList = modules.toList())

    constructor(vararg modules: KClass<out Module>) : this(moduleClassList = modules.toList())
}

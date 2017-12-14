package misk.testing

import com.google.inject.Module
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@ExtendWith(MiskTestExtension::class)
/**
 * Annotate your test classes with `@MiskTest` to have fields annotated with `@Inject` initialized.
 * Provide zero or more [Module] classes to be installed with [MiskTest.modules], or
 * a [ModuleProvider] with [MiskTest.moduleProvider], which can provide instantiated module
 * instances. You may use a combination of the two approaches as well.
 */
annotation class MiskTest (
        vararg val modules: KClass<out Module>,
        val moduleProvider: KClass<out ModuleProvider> = DefaultModuleProvider::class)

private class DefaultModuleProvider : ModuleProvider {
    override fun modules(): Array<out Module> {
        // By default, no modules to install.
        return arrayOf()
    }
}

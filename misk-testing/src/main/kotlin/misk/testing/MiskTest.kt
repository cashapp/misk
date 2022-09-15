package misk.testing

import com.google.inject.Module
import com.google.inject.util.Modules
import org.junit.jupiter.api.extension.ExtendWith

@Target(AnnotationTarget.CLASS)
@ExtendWith(MiskTestExtension::class)

/**
 * Annotate your test classes with `@MiskTest` to have fields annotated with `@Inject` initialized.
 * Provide modules to be installed by annotating a [Module] field in your test class with
 * [MiskTestModule]. This can be a compound module, composed using [Modules.combine].
 *
 * Configure test callbacks with Guice multibindings. Register instances by calling `multibind()`
 * in a `KAbstractModule`:
 *
 * ```
 * multibind<BeforeEachCallback>().to<MyBeforeEach>()
 * multibind<AfterEachCallback>().to<MyAfterEach>()
 * ```
 */
annotation class MiskTest(val startService: Boolean = false)

@Target(AnnotationTarget.FIELD)
annotation class MiskTestModule

@Target(AnnotationTarget.FIELD)
annotation class MiskExternalDependency

package misk.inject

import com.google.inject.util.Modules
import java.util.SortedSet

/**
 * Allows overriding reusable test modules when reusing injectors in Misk tests.
 *
 * Usage:
 * ```
 * @MiskTestModule
 * val module = overrideReusable(OriginalReusableTestModule()).with(OverrideTestModuleOne(), OverrideTestModuleOne(), ...)
 * ```
 */
fun overrideReusable(vararg modules: ReusableTestModule) =
  ReusableOverriddenModuleBuilder(modules = modules.toSortedSet(ReusableTestModuleComparator))

class ReusableOverridesTestModule(
  private val originalTestModules: SortedSet<ReusableTestModule>,
  private val overrides: SortedSet<ReusableTestModule>,
) : ReusableTestModule() {
  override fun configure() {
    install(Modules.override(originalTestModules).with(overrides))
  }
}

private object ReusableTestModuleComparator : Comparator<ReusableTestModule> {
  override fun compare(o1: ReusableTestModule, o2: ReusableTestModule): Int {
    return o1.javaClass.name.compareTo(o2.javaClass.name)
  }
}

class ReusableOverriddenModuleBuilder(private val modules: SortedSet<ReusableTestModule>) {

  fun with(vararg overrides: ReusableTestModule) =
    ReusableOverridesTestModule(
      originalTestModules = modules,
      overrides = overrides.toSortedSet(ReusableTestModuleComparator),
    )
}

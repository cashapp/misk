package misk.scope

import com.google.inject.name.Named
import com.google.inject.name.Names
import javax.inject.Inject

class TestActionScopedProviderModule : ActionScopedProviderModule() {
  override fun configureProviders() {
    bindSeedData(String::class, Names.named("from-seed"))
    bindProvider(String::class, Names.named("foo"), FooProvider::class)
    bindProvider(String::class, Names.named("bar"), BarProvider::class)
  }

  class BarProvider @Inject internal constructor(
      @Named("from-seed") val seedData: ActionScoped<String>
  ) : ActionScopedProvider<String> {
    override fun get(): String = "${seedData.get()} and bar"
  }

  class FooProvider @Inject internal constructor(
      @Named("bar") val bar: ActionScoped<String>
  ) : ActionScopedProvider<String> {
    override fun get(): String = "${bar.get()} and foo!"
  }
}

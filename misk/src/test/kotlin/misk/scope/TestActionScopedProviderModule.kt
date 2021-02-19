package misk.scope

import com.google.inject.TypeLiteral
import com.google.inject.name.Named
import com.google.inject.name.Names
import misk.exceptions.UnauthenticatedException
import javax.inject.Inject

class TestActionScopedProviderModule : ActionScopedProviderModule() {
  override fun configureProviders() {
    bindSeedData(String::class, Names.named("from-seed"))
    bindProvider(String::class, FooProvider::class, Names.named("foo"))
    bindProvider(String::class, BarProvider::class, Names.named("bar"))
    bindProvider(String::class, ZedProvider::class, Names.named("zed"))
    bindProvider(nullableStringTypeLiteral, NullableFooProvider::class, Names.named("nullable-foo"))
    bindProvider(
      nullableStringTypeLiteral, NullableBasedOnFooProvider::class,
      Names.named("nullable-based-on-foo")
    )
  }

  class BarProvider @Inject internal constructor(
    @Named("from-seed") private val seedData: ActionScoped<String>
  ) : ActionScopedProvider<String> {
    override fun get(): String = "${seedData.get()} and bar"
  }

  class FooProvider @Inject internal constructor(
    @Named("bar") private val bar: ActionScoped<String>
  ) : ActionScopedProvider<String> {
    override fun get(): String = "${bar.get()} and foo!"
  }

  class ZedProvider @Inject internal constructor(
    @Named("from-seed") private val seedData: ActionScoped<String>
  ) : ActionScopedProvider<String> {
    override fun get(): String {
      val seedData = seedData.get()
      if (seedData == "unauthenticated") throw UnauthenticatedException()
      return "$seedData and zed*"
    }
  }

  class NullableFooProvider @Inject internal constructor(
    @Named("from-seed") private val seedData: ActionScoped<String>
  ) : ActionScopedProvider<String?> {
    override fun get(): String? {
      val seedData = seedData.get()
      return if (seedData == "null") return null else "$seedData and foo"
    }
  }

  class NullableBasedOnFooProvider @Inject internal constructor(
    @Named("nullable-foo") private val nullableFoo: ActionScoped<String?>
  ) : ActionScopedProvider<String?> {
    override fun get(): String? {
      return nullableFoo.get()?.let { "from foo $it" }
    }
  }

  companion object {
    val nullableStringTypeLiteral = object : TypeLiteral<String?>() {}
  }
}

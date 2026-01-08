package misk.inject.guice7

import com.google.inject.Inject
import com.google.inject.Provider
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat

@Singleton
internal class Guice7TestClass
@Inject
constructor(
  jakartaInjectJakartaProvider: jakarta.inject.Provider<JakartaConstructor>,
  jakartaInjectGuiceProvider: Provider<JakartaConstructor>,
  guiceInjectJakartaProvider: jakarta.inject.Provider<GuiceConstructor>,
  guiceInjectGuiceProvider: Provider<GuiceConstructor>,
) : Guice7TestInterface {
  init {
    assertThat(jakartaInjectJakartaProvider.get()).isSameAs(jakartaInjectGuiceProvider.get())

    assertThat(guiceInjectJakartaProvider.get()).isSameAs(guiceInjectGuiceProvider.get())
  }

  @Singleton internal class JakartaConstructor @jakarta.inject.Inject constructor() : Guice7TestInterface.Multibind

  @jakarta.inject.Singleton internal class GuiceConstructor @Inject constructor() : Guice7TestInterface.Multibind
}

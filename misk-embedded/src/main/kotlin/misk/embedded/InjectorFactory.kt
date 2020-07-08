package misk.embedded

import com.google.inject.Injector

interface InjectorFactory {
  fun createInjector(): Injector
}

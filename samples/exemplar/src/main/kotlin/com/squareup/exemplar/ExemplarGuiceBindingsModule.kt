package com.squareup.exemplar

import com.google.inject.Provides
import com.google.inject.name.Named
import com.google.inject.name.Names
import misk.inject.KAbstractModule

class ExemplarGuiceBindingsModule : KAbstractModule() {
  override fun configure() {
    bind<Service>().to<ServiceImpl>()
    bind<Service>()
      .annotatedWith(Names.named("AnotherService"))
      .to<AnotherServiceImpl>()
    bind<String>()
      .annotatedWith(Names.named("ConstantBindingExample"))
      .toInstance("MyString")
    bind<Service>()
      .annotatedWith(Names.named("ServiceInCompanionClass"))
      .to<MyCompanionService>()
  }

  @Provides
  @Named("YetAnotherService")
  fun provideYetAnotherService(): Service = AnotherServiceImpl()

  companion object {
    class MyCompanionService : Service {
      override fun execute() = "ServiceInCompanionClass"
    }
  }
}

interface Service {
  fun execute(): String
}

// Implementations of the interface
class ServiceImpl : Service {
  override fun execute() = "ServiceImpl executed"
}

class AnotherServiceImpl : Service {
  override fun execute() = "AnotherServiceImpl executed"
}

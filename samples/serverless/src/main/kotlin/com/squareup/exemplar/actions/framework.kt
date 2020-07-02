package com.squareup.exemplar.actions

import com.google.inject.multibindings.Multibinder
import misk.inject.KAbstractModule
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy.RUNTIME
import javax.inject.Qualifier
import kotlin.reflect.KClass

@Qualifier
@Documented
@Retention(RUNTIME)
annotation class CashServerlessModule

class DynamoDbClientModule(
  val annotationClass: KClass<*>,
  val name: String
) : KAbstractModule() {
  override fun configure() {
//    multibind<DynamoDbDependency>().toInstance()
    Multibinder.newSetBinder(binder(), DynamoDbDependency::class.java).addBinding().toInstance(DynamoDbDependency(annotationClass))
  }
}

data class DynamoDbDependency(
  val annotation: KClass<*>
)

interface DynamoDb {
  fun get(key: String): String?
  fun put(key: String, value: String?)
}

abstract class ServerlessComponent(val name: String) : KAbstractModule()

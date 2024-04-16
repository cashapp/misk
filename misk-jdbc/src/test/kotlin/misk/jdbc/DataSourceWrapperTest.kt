package misk.jdbc

import misk.mockito.Mockito.whenever
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.io.PrintWriter
import java.sql.ConnectionBuilder
import java.sql.ShardingKeyBuilder
import java.util.logging.Logger
import javax.sql.DataSource
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.superclasses
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.isAccessible

class DataSourceWrapperTest {
  @Test
  fun `wrap a data source`() {
    val dataSource = mock<DataSource> {
      on { getLogWriter() } doReturn mock<PrintWriter>()
      on { getLoginTimeout() } doReturn 10
      on { getParentLogger() } doReturn mock<Logger>()
      on { getConnection() } doReturn mock<java.sql.Connection>()
      on { getConnection(anyString(), anyString()) } doReturn mock<java.sql.Connection>()
      on { createConnectionBuilder() } doReturn mock<ConnectionBuilder>()
      on { createShardingKeyBuilder() } doReturn mock<ShardingKeyBuilder>()
    }

    val wrapper = DataSourceWrapper("qualifier")
    wrapper.initialize(dataSource)

    getAllInterfaceMethods(DataSource::class).forEach { function ->
      function.isAccessible = true
      val callArgs = function.valueParameters.map { param ->
        provideDummyValue(param.type.classifier as KClass<*>)
      }.toTypedArray()
      function.call(wrapper, *callArgs)
      verify(dataSource, atLeast(1)).let {
        val verifyArgs = function.valueParameters.map { param ->
          provideMatcher(param.type.classifier as KClass<*>)
        }.toTypedArray()
        function.call(it, *verifyArgs)
      }
    }
  }

  @Test
  fun `throws when not initialized`() {
    val wrapper = DataSourceWrapper("qualifier")
    assertThrows<IllegalStateException> { wrapper.getLogWriter() }
  }

  private fun provideMatcher(clazz: KClass<*>): Any {
    return when (clazz) {
      Int::class -> anyInt()
      else -> any()
    }
  }

  private fun provideDummyValue(clazz: KClass<*>): Any {
    return when (clazz) {
      String::class -> "dummy"
      Int::class -> 10
      PrintWriter::class -> mock<PrintWriter>()
      Class::class -> Class::class.java
      // Add more types as necessary
      else -> throw IllegalArgumentException("Unsupported parameter type: $clazz")
    }
  }

  private fun getAllInterfaceMethods(kClass: KClass<*>): Set<KFunction<*>> {
    val methods = mutableSetOf<KFunction<*>>()

    // Recursively add methods from all superinterfaces
    fun addMethodsFromClass(currentClass: KClass<*>) {
      if (currentClass.java.isInterface) {
        methods.addAll(currentClass.declaredFunctions)
        currentClass.superclasses.forEach { superClass ->
          addMethodsFromClass(superClass)
        }
      }
    }

    addMethodsFromClass(kClass)
    return methods
  }
}

package misk.inject

import com.google.inject.CreationException
import com.google.inject.Guice
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class RequireBindingTest {
  @Test
  fun `throws exception if requested binding is missing`() {
    assertThrows<CreationException> {
      Guice.createInjector(RequireBindingTestingModule()).injectMembers(this)
    }
  }

  @Test
  fun `throws exception if requested binding with wrong annotation is provided`() {
    assertThrows<CreationException> {
      Guice.createInjector(RequireBindingTestingModule(), object : KAbstractModule() {
        override fun configure() {
          bind<Color>().annotatedWith<TestAnnotation2>().to<Red>()
        }
      }).injectMembers(this)
    }
  }

  @Test
  fun `does not throws exception if requested binding with correct annotation is provided`() {
    assertDoesNotThrow {
      Guice.createInjector(RequireBindingTestingModule(), object : KAbstractModule() {
        override fun configure() {
          bind<Color>().annotatedWith<TestAnnotation>().to<Blue>()
        }
      }).injectMembers(this)
    }
  }
}

class RequireBindingTestingModule : KAbstractModule() {
  override fun configure() {
    requireBindingWithAnnotation<Color, TestAnnotation>()
  }
}

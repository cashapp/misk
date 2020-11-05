package misk.mockito

import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.stubbing.OngoingStubbing

object Mockito {
  inline fun <reified T : Any> mock(): T = Mockito.mock(T::class.java)

  fun <T> whenever(t: T): OngoingStubbing<T> = `when`(t)

  inline fun <reified T : Any> captor(): ArgumentCaptor<T> = ArgumentCaptor.forClass(T::class.java)

  inline fun <reified T> any(): T = Mockito.any<T>(T::class.java)

  fun <T> eq(t: T): T = Mockito.eq<T>(t)
}

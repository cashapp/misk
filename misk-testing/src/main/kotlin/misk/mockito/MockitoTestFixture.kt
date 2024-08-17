package misk.mockito

import com.google.inject.Provider
import misk.testing.TestFixture
import org.mockito.Mockito

class MockitoTestFixture(private val mockProvider: Provider<out Any>) : TestFixture {
  override fun reset() {
    Mockito.reset(mockProvider.get())
  }
}

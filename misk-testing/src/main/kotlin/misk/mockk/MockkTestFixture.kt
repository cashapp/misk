package misk.mockk

import io.mockk.clearMocks
import misk.testing.FakeFixture

open class MockkTestFixture<T : Any> @JvmOverloads constructor(
  private val mock: T,
  private val setUp: T.() -> Unit = {}
) : FakeFixture() {

  init {
    mock.setUp()
  }

  override fun reset() {
    super.reset()
    clearMocks(mock)
    mock.setUp()
    initMock()
  }

  /**
   * An alternative to the `setUp` lambda that can be overridden by subclasses in places where an inline setUp lambda
   * cannot be provided.
    */
  open fun initMock() {}
}

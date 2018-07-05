package misk

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.createType
import kotlin.test.assertFailsWith

internal class ActionsTest {
  @Test
  fun methodAsAction() {
    val action = TestAction::myActionMethod.asAction()
    assertThat(action.name).isEqualTo("TestAction")
    assertThat(action.parameterTypes).hasSize(1)
    assertThat(action.parameterTypes[0]).isEqualTo(Int::class.createType())
    assertThat(action.returnType).isEqualTo(String::class.createType())
  }

  @Test
  fun methodReferenceNotAllowedAsAction() {
    assertFailsWith<IllegalArgumentException> {
      val t = TestAction()
      t::myActionMethod.asAction()
    }
  }

  @Test
  fun freeStandingFunctionNotAllowedAsAction() {
    assertFailsWith<IllegalArgumentException> {
      ::myActionHandler.asAction()
    }
  }

  class TestAction {
    fun myActionMethod(n: Int): String = "foo$n"
  }

  fun myActionHandler(n: Int): String = "bar$n"
}

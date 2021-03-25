package misk

import misk.web.DispatchMechanism
import misk.web.RequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.createType
import kotlin.test.assertFailsWith

internal class ActionsTest {
  @Test fun methodAsAction() {
    val action = TestAction::myActionMethod.asAction(DispatchMechanism.GET)
    assertThat(action.name).isEqualTo("TestAction")
    assertThat(action.parameterTypes).hasSize(2)
    assertThat(action.parameterTypes[0]).isEqualTo(Int::class.createType())
    assertThat(action.parameterTypes[1]).isEqualTo(String::class.createType())
    assertThat(action.requestType).isEqualTo(String::class.createType())
    assertThat(action.returnType).isEqualTo(String::class.createType())
  }

  @Test fun noRequestBodyAction() {
    val action = NoRequestBodyAction::myActionMethod.asAction(DispatchMechanism.GET)
    assertThat(action.name).isEqualTo("NoRequestBodyAction")
    assertThat(action.parameterTypes).hasSize(1)
    assertThat(action.parameterTypes[0]).isEqualTo(Int::class.createType())
    assertThat(action.requestType).isNull()
  }

  @Test fun nestedAction() {
    val action = NestedAction.InnerAction::myActionMethod.asAction(DispatchMechanism.GET)
    assertThat(action.name).isEqualTo("NestedAction.InnerAction")
  }

  @Test fun methodReferenceNotAllowedAsAction() {
    assertFailsWith<IllegalArgumentException> {
      val t = TestAction()
      t::myActionMethod.asAction(DispatchMechanism.GET)
    }
  }

  @Test fun freeStandingFunctionNotAllowedAsAction() {
    assertFailsWith<IllegalArgumentException> {
      ::myActionHandler.asAction(DispatchMechanism.GET)
    }
  }
}

class TestAction {
  fun myActionMethod(n: Int, @RequestBody req: String): String = "foo$n$req"
}

class NoRequestBodyAction {
  fun myActionMethod(n: Int): String = "foo$n"
}

class NestedAction {
  fun myActionMethod(n: Int, @RequestBody req: String): String = "foo$n$req"

  class InnerAction {
    fun myActionMethod(n: Int, @RequestBody req: String): String = "foo$n$req"
  }
}

fun myActionHandler(n: Int): String = "bar$n"

package misk

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class ActionsTest {
    @Test
    fun methodAsAction() {
        val action = TestAction::myActionMethod.asAction()
        assertThat(action.name).isEqualTo("TestAction")
        assertThat(action.parameterTypes).hasSize(1)
        assertThat(action.parameterTypes[0].type).isEqualTo(Int::class.java)
        assertThat(action.returnType.type).isEqualTo(String::class.java)
    }

    @Test
    fun methodReferenceNotAllowedAsAction() {
        assertThrows(IllegalArgumentException::class.java) {
            val t = TestAction()
            t::myActionMethod.asAction()
        }
    }

    @Test
    fun freeStandingFunctionNotAllowedAsAction() {
        assertThrows(IllegalArgumentException::class.java) {
            ::myActionHandler.asAction()
        }
    }

    class TestAction {
        fun myActionMethod(n: Int): String = "foo$n"
    }

    fun myActionHandler(n: Int): String = "bar$n"
}

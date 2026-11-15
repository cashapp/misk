package misk.web.marshal

import java.lang.reflect.WildcardType
import kotlin.reflect.full.functions
import kotlin.reflect.typeOf
import misk.web.Response
import misk.web.ResponseBody
import misk.web.marshal.Marshaller.Companion.actualResponseType
import okio.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ActualResponseTypeTest {
  @Test
  fun `unwraps Response to String`() {
    assertThat(actualResponseType(typeOf<Response<String>>())).isEqualTo(String::class.java)
  }

  @Test
  fun `unwraps Response to ResponseBody`() {
    assertThat(actualResponseType(typeOf<Response<ResponseBody>>())).isEqualTo(ResponseBody::class.java)
  }

  @Test
  fun `unwraps Response to ByteString`() {
    assertThat(actualResponseType(typeOf<Response<ByteString>>())).isEqualTo(ByteString::class.java)
  }

  @Test
  fun `handles bare String without Response wrapper`() {
    assertThat(actualResponseType(typeOf<String>())).isEqualTo(String::class.java)
  }

  @Test
  fun `handles bare ResponseBody without Response wrapper`() {
    assertThat(actualResponseType(typeOf<ResponseBody>())).isEqualTo(ResponseBody::class.java)
  }

  @Test
  fun `suspend function returning Response of ResponseBody resolves correctly`() {
    val fn = SuspendActions::class.functions.first { it.name == "responseBody" }
    val resolved = actualResponseType(fn.returnType)
    assertThat(resolved).isNotInstanceOf(WildcardType::class.java)
    assertThat(resolved).isEqualTo(ResponseBody::class.java)
  }

  @Test
  fun `suspend function returning Response of String resolves correctly`() {
    val fn = SuspendActions::class.functions.first { it.name == "responseString" }
    val resolved = actualResponseType(fn.returnType)
    assertThat(resolved).isNotInstanceOf(WildcardType::class.java)
    assertThat(resolved).isEqualTo(String::class.java)
  }

  @Test
  fun `suspend function returning Response of ByteString resolves correctly`() {
    val fn = SuspendActions::class.functions.first { it.name == "responseByteString" }
    val resolved = actualResponseType(fn.returnType)
    assertThat(resolved).isNotInstanceOf(WildcardType::class.java)
    assertThat(resolved).isEqualTo(ByteString::class.java)
  }

  @Test
  fun `non-suspend function returning Response of ResponseBody resolves correctly`() {
    val fn = NonSuspendActions::class.functions.first { it.name == "responseBody" }
    val resolved = actualResponseType(fn.returnType)
    assertThat(resolved).isNotInstanceOf(WildcardType::class.java)
    assertThat(resolved).isEqualTo(ResponseBody::class.java)
  }

  @Suppress("unused")
  class SuspendActions {
    suspend fun responseBody(): Response<ResponseBody> = TODO()
    suspend fun responseString(): Response<String> = TODO()
    suspend fun responseByteString(): Response<ByteString> = TODO()
  }

  @Suppress("unused")
  class NonSuspendActions {
    fun responseBody(): Response<ResponseBody> = TODO()
  }
}

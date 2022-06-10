package misk.policy.opa

import com.google.inject.Module
import com.google.inject.Provides
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import misk.mockito.Mockito
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.mediatype.MediaTypes.APPLICATION_JSON
import misk.web.mediatype.asMediaType
import okhttp3.ResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyString
import retrofit2.Response
import retrofit2.mock.Calls
import wisp.moshi.defaultKotlinMoshi
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@MiskTest(startService = false)
internal class RealOpaPolicyEngineTest {
  @MiskTestModule val module: Module = object : KAbstractModule() {
    override fun configure() {
      bind<OpaPolicyEngine>().to<RealOpaPolicyEngine>()
    }

    @Provides @Singleton
    fun opaApi(): OpaApi = Mockito.mock()

    @Provides @Singleton @Named("opa-moshi")
    fun provideMoshi(): Moshi {
      return defaultKotlinMoshi
    }
  }

  @Inject lateinit var opaApi: OpaApi
  @Inject lateinit var opaPolicyEngine: OpaPolicyEngine

  @Test
  fun emptyInputQuery() {
    Mockito.whenever(opaApi.queryDocument(anyString(), anyString(), anyBoolean())).thenReturn(
      Calls.response(
        ResponseBody.create(
          APPLICATION_JSON.asMediaType(),
          "{\"decision_id\": \"decisionIdString\", \"result\": {\"test\": \"a\"}}"
        )
      )
    )
    val evaluate: BasicResponse = opaPolicyEngine.evaluate("test")
    assertThat(evaluate).isEqualTo(BasicResponse("a"))
  }

  @Test
  fun pojoInputQuery() {
    val requestCaptor = Mockito.captor<String>()
    Mockito.whenever(opaApi.queryDocument(anyString(), capture(requestCaptor), anyBoolean())).thenReturn(
      Calls.response(
        ResponseBody.create(
          APPLICATION_JSON.asMediaType(),
          "{\"decision_id\": \"decisionIdString\", \"result\": {\"test\": \"a\"}}"
        )
      )
    )

    val evaluate: BasicResponse = opaPolicyEngine.evaluate("test", BasicRequest(1))

    assertThat(evaluate).isEqualTo(BasicResponse("a"))
    assertThat(requestCaptor.value).isEqualTo("{\"input\":{\"someValue\":1}}")
  }

  @Test
  fun responseIsNotOk() {
    Mockito.whenever(opaApi.queryDocument(anyString(), anyString(), anyBoolean())).thenReturn(
      Calls.response(
        Response.error(
          403,
          ResponseBody.create(APPLICATION_JSON.asMediaType(), "Access Denied")
        )
      )
    )

    val exception = assertThrows<PolicyEngineException> {
      val evaluate: BasicResponse = opaPolicyEngine.evaluate("test", BasicRequest(1))
    }
    assertThat(exception.message).isEqualTo("[403]: Access Denied")
  }

  @Test
  fun unableToParseResponseIntoRequestedShape() {
    Mockito.whenever(opaApi.queryDocument(anyString(), anyString(), anyBoolean())).thenReturn(
      Calls.response(
        ResponseBody.create(
          APPLICATION_JSON.asMediaType(),
          "{\"decision_id\": \"decisionIdString\", \"result\": {\"wrongItem\": 1}}"
        )
      )
    )

    val exception = assertThrows<PolicyEngineException> {
      opaPolicyEngine.evaluate<BasicRequest, BasicResponse>("test", BasicRequest(1))
    }
    assertThat(exception.cause).isInstanceOf(JsonDataException::class.java)
    assertThat(exception.cause!!.message).isEqualTo("Required value 'test' missing at \$.result")
    assertThat(exception.message).isEqualTo("Response shape did not match")
  }

  @Test
  fun unknownPolicyDocument() {
    Mockito.whenever(opaApi.queryDocument(anyString(), anyString(), anyBoolean())).thenReturn(
      Calls.response(
        ResponseBody.create(
          APPLICATION_JSON.asMediaType(),
          "{\"decision_id\": \"decisionIdString\"}"
        )
      )
    )

    val exception = assertThrows<PolicyEngineException> {
      opaPolicyEngine.evaluate<BasicRequest, BasicResponse>("test", BasicRequest(1))
    }
    assertThat(exception.message).isEqualTo("Policy document \"test\" not found.")
  }

  @Test
  fun throwsExceptionIfNoDocumentSpecified() {
    val exception = assertThrows<IllegalArgumentException> {
      opaPolicyEngine.evaluate<BasicRequest, BasicResponse>("", BasicRequest(1))
    }
    assertThat(exception.message).isEqualTo("Must specify document")
  }

  // Weird kotlin workaround for mockito. T must not be nullable.
  private fun <T> capture(argumentCaptor: ArgumentCaptor<T>): T = argumentCaptor.capture()

  data class BasicResponse(val test: String) : OpaResponse
  data class BasicRequest(val someValue: Int) : OpaRequest
}

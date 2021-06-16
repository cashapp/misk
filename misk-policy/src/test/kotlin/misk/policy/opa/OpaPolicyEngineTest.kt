package misk.policy.opa

import com.github.dockerjava.core.DockerClientBuilder
import com.google.inject.Module
import com.google.inject.util.Modules
import com.squareup.moshi.JsonDataException
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.mockito.Mockito
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.mediatype.MediaTypes.APPLICATION_JSON
import misk.web.mediatype.asMediaType
import okhttp3.ResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.anyString
import retrofit2.Response
import retrofit2.mock.Calls
import javax.inject.Inject

@MiskTest(startService = false)
internal class OpaPolicyEngineTest {
//  @MiskTestModule val module: Module = object : KAbstractModule() {
//    @Provides @Singleton
//    fun opaApi(): OpaApi = Mockito.mock()
//
//    @Provides @Singleton @Named("opa-moshi")
//    fun provideMoshi(): Moshi {
//      return Moshi.Builder()
//        .add(KotlinJsonAdapterFactory())
//        .build()
//    }
//  }

  @BeforeEach internal fun setUp() {
    val dockerClient = DockerClientBuilder.getInstance().build()
    val opaContainer = OpaContainer(dockerClient)
    opaContainer.start()
  }


  @MiskTestModule val module: Module = Modules.combine(
    MiskTestingServiceModule(),
    object : KAbstractModule() {
      override fun configure() {
        install(OpaModule(OpaConfig(baseUrl = "http://localhost:8181", unixSocket = "")))
      }
    }
  )

  @Inject lateinit var opaApi: OpaApi
  @Inject lateinit var opaPolicyEngine: OpaPolicyEngine


  @Test
  fun emptyInputQuery() {
    Mockito.whenever(opaApi.queryDocument(anyString(), anyString())).thenReturn(
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

  data class BasicResponsePV(val allow: Boolean, val test_allow: Boolean)
  @Test
  fun pojoInputQuery() {
//    val requestCaptor = Mockito.captor<String>()
//    Mockito.whenever(opaApi.queryDocument(anyString(), capture(requestCaptor))).thenReturn(
//      Calls.response(
//        ResponseBody.create(
//          APPLICATION_JSON.asMediaType(),
//          "{\"decision_id\": \"decisionIdString\", \"result\": {\"test\": \"a\"}}"
//        )
//      )
//    )


    val evaluate: BasicResponsePV = opaPolicyEngine.evaluate("demo", BasicRequest(1))

    assertThat(evaluate).isEqualTo(BasicResponse("a"))
//    assertThat(requestCaptor.value).isEqualTo("{\"input\":{\"someValue\":1}}")
  }

  @Test
  fun responseIsNotOk() {
    Mockito.whenever(opaApi.queryDocument(anyString(), anyString())).thenReturn(
      Calls.response(
        Response.error(
          403,
          ResponseBody.create(APPLICATION_JSON.asMediaType(), "Access Denied")
        )
      )
    )

    val exception = assertThrows<PolicyEngineException> {
      opaPolicyEngine.evaluate("test", BasicRequest(1))
    }
    assertThat(exception.message).isEqualTo("[403]: Access Denied")
  }

  @Test
  fun unableToParseResponseIntoRequestedShape() {
    Mockito.whenever(opaApi.queryDocument(anyString(), anyString())).thenReturn(
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
    Mockito.whenever(opaApi.queryDocument(anyString(), anyString())).thenReturn(
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

  data class BasicResponse(val test: String)
  data class BasicRequest(val someValue: Int)

  @Test
  fun justTestingDocker() {
    val dockerClient = DockerClientBuilder.getInstance().build()
    val opaContainer = OpaContainer(dockerClient)
    opaContainer.start()
//    opaApi.queryDocument(anyString(), anyString())
//    opaPolicyEngine.evaluate("test", BasicRequest(1))
    opaContainer.stop()
  }
}

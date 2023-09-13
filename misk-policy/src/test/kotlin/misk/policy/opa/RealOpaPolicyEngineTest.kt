package misk.policy.opa

import com.google.inject.Module
import com.google.inject.Provides
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import misk.metrics.v2.FakeMetrics
import misk.metrics.v2.FakeMetricsModule
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
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import okhttp3.ResponseBody.Companion.toResponseBody

@MiskTest(startService = false)
internal class RealOpaPolicyEngineTest {
  @MiskTestModule val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(FakeMetricsModule())
      bind<OpaMetrics>().to<MiskOpaMetrics>()
      bind<OpaPolicyEngine>().to<RealOpaPolicyEngine>()
    }

    @Provides @Singleton
    fun opaConfig(): OpaConfig {
      return OpaConfig("fake", null, true, true)
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
  @Inject lateinit var fakeMetrics: FakeMetrics

  val metricsPayload = """
, "metrics": {
    "counter_server_query_cache_hit": 1,
    "timer_rego_external_resolve_ns": 6000,
    "timer_rego_input_parse_ns": 9833,
    "timer_rego_query_eval_ns": 283083,
    "timer_server_handler_ns": 582042
}    
  """.trimIndent()

  @Test
  fun metricsInResponse() {
    Mockito.whenever(opaApi.queryDocument(anyString(), anyString(), anyBoolean(), anyBoolean()))
      .thenReturn(
        Calls.response(
          "{\"decision_id\": \"decisionIdString\", \"result\": {\"test\": \"a\"} ${metricsPayload}}"
            .toResponseBody(APPLICATION_JSON.asMediaType())
        )
      )

    val evaluate: BasicResponse = opaPolicyEngine.evaluate("test")

    assertThat(evaluate).isEqualTo(BasicResponse("a"))
    assertThat(evaluate.metrics).isNotNull

    assertThat(
      fakeMetrics.get(
        MiskOpaMetrics.MetricType.opa_server_query_cache_hit.name,
        "document" to "test"
      )
    )
      .isEqualTo(1.0)
    assertThat(
      fakeMetrics.summaryCount(
        MiskOpaMetrics.MetricType.opa_rego_query_eval_ns.name,
        "document" to "test"
      )
    )
      .isEqualTo(1.0)
    assertThat(
      fakeMetrics.summaryMean(
        MiskOpaMetrics.MetricType.opa_rego_query_eval_ns.name,
        "document" to "test"
      )
    )
      .isEqualTo(2.83083E-4)

  }

  @Test
  fun emptyInputQuery() {
    Mockito.whenever(opaApi.queryDocument(anyString(), anyString(), anyBoolean(), anyBoolean())).thenReturn(
      Calls.response(
        "{\"decision_id\": \"decisionIdString\", \"result\": {\"test\": \"a\"}}"
          .toResponseBody(APPLICATION_JSON.asMediaType())
      )
    )
    val evaluate: BasicResponse = opaPolicyEngine.evaluate("test")
    assertThat(evaluate).isEqualTo(BasicResponse("a"))
  }

  @Test
  fun pojoInputQuery() {
    val requestCaptor = Mockito.captor<String>()
    Mockito.whenever(opaApi.queryDocument(anyString(), capture(requestCaptor), anyBoolean(), anyBoolean())).thenReturn(
      Calls.response(
        "{\"decision_id\": \"decisionIdString\", \"result\": {\"test\": \"a\"}}"
          .toResponseBody(APPLICATION_JSON.asMediaType())
      )
    )

    val evaluate: BasicResponse = opaPolicyEngine.evaluate("test", BasicRequest(1))

    assertThat(evaluate).isEqualTo(BasicResponse("a"))
    assertThat(requestCaptor.value).isEqualTo("{\"input\":{\"someValue\":1}}")
  }

  @Test
  fun rawJsonInputQuery() {
    val requestCaptor = Mockito.captor<String>()
    Mockito.whenever(opaApi.queryDocument(anyString(), capture(requestCaptor), anyBoolean(), anyBoolean())).thenReturn(
      Calls.response(
        "{\"decision_id\": \"decisionIdString\", \"result\": {\"test\": \"a\"}}"
          .toResponseBody(APPLICATION_JSON.asMediaType())
      )
    )

    val evaluate: BasicResponse = opaPolicyEngine.evaluate("test", "{\"input\":\"someValue\"}")

    assertThat(evaluate).isEqualTo(BasicResponse("a"))
    assertThat(requestCaptor.value).isEqualTo("{\"input\":\"someValue\"}")
  }

  @Test
  fun responseIsNotOk() {
    Mockito.whenever(opaApi.queryDocument(anyString(), anyString(), anyBoolean(), anyBoolean())).thenReturn(
      Calls.response(
        Response.error(
          403,
          "Access Denied".toResponseBody(APPLICATION_JSON.asMediaType())
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
    Mockito.whenever(opaApi.queryDocument(anyString(), anyString(), anyBoolean(), anyBoolean())).thenReturn(
      Calls.response(
        "{\"decision_id\": \"decisionIdString\", \"result\": {\"wrongItem\": 1}}"
          .toResponseBody(APPLICATION_JSON.asMediaType())
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
    Mockito.whenever(opaApi.queryDocument(anyString(), anyString(), anyBoolean(), anyBoolean())).thenReturn(
      Calls.response(
        "{\"decision_id\": \"decisionIdString\"}"
          .toResponseBody(APPLICATION_JSON.asMediaType())
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

  @Test
  fun returnsProvenanceBundleIfSpecified() {
    Mockito.whenever(opaApi.queryDocument(anyString(), anyString(), anyBoolean(), anyBoolean())).thenReturn(
      Calls.response(
        ("{\"provenance\":{\"version\":\"0.30.1\",\"build_commit\":\"03b0b1f\",\"bundles\"" +
          ":{\"xyz\":{\"revision\": \"revision123\"}}}, \"decision_id\": \"decisionIdString\"," +
          "\"result\": {\"test\": \"a\"}}"
          ).toResponseBody(APPLICATION_JSON.asMediaType())
      )
    )
    val evaluate: BasicResponse = opaPolicyEngine.evaluate("test", BasicRequest(1))
    assertThat(evaluate).isEqualTo(BasicResponse("a"))
    assertThat(evaluate.provenance?.bundles).isNotNull
    assertThat(evaluate.provenance?.bundles?.get("xyz")?.revision ?: "").isEqualTo("revision123")
  }

  @Test
  fun returnsProvenanceRevisionIfSpecified() {
    Mockito.whenever(opaApi.queryDocument(anyString(), anyString(), anyBoolean(), anyBoolean())).thenReturn(
      Calls.response(
        ("{\"provenance\":{\"version\":\"0.30.1\",\"build_commit\":\"03b0b1f\",\"revision\":" +
          " \"revision123\"}, \"decision_id\": \"decisionIdString\"," +
          "\"result\": {\"test\": \"a\"}}"
          ).toResponseBody(APPLICATION_JSON.asMediaType())
      )
    )
    val evaluate: BasicResponse = opaPolicyEngine.evaluate("test", BasicRequest(1))
    assertThat(evaluate).isEqualTo(BasicResponse("a"))
    assertThat(evaluate.provenance?.bundles).isNull()
    assertThat(evaluate.provenance?.revision ?: "").isEqualTo("revision123")
  }

  // Weird kotlin workaround for mockito. T must not be nullable.
  private fun <T> capture(argumentCaptor: ArgumentCaptor<T>): T = argumentCaptor.capture()

  data class BasicResponse(val test: String) : OpaResponse()
  data class BasicRequest(val someValue: Int) : OpaRequest()
}

package misk.policy.opa

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.ResponseBody
import jakarta.inject.Inject
import jakarta.inject.Named

/**
 * Support for the Open Policy Engine (OPA).
 * OPA provides a means to decouple policy from business logic. The resulting query response may
 * have arbitrary shapes.
 */
class RealOpaPolicyEngine @Inject constructor(
  private val opaApi: OpaApi,
  @Named("opa-moshi") private val moshi: Moshi,
  private val metrics: OpaMetrics,
  private val config: OpaConfig
) : OpaPolicyEngine {

  /**
   * Evaluate / Query a document with given input of shape T.
   * This will connect to OPA via a retrofit interface and perform a /v1/data/{document} POST.
   *
   * @param document Name or Path of the OPA document to query.
   * @param input Input data to be supplied to OPA at evaluation time (the input global field).
   * @param inputType Input shape to be JSONified for OPA
   * @param returnType Return shape to be JSONified from OPA
   * @throws PolicyEngineException if the request to OPA failed or the response shape didn't match R.
   * @throws IllegalArgumentException if no document path was specified.
   * @return Response shape R from OPA.
   */
  override fun <T : OpaRequest, R : OpaResponse> evaluateWithInput(
    document: String,
    input: T,
    inputType: Class<T>,
    returnType: Class<R>
  ): R {
    return evaluateInternal(document, input, inputType, returnType)
  }

  private fun <T : OpaRequest, R : OpaResponse> evaluateInternal(
    document: String,
    input: T,
    inputType: Class<T>,
    returnType: Class<R>
  ): R {
    if (document.isEmpty()) {
      throw IllegalArgumentException("Must specify document")
    }

    val inputAdapter = moshi.adapter<Request<T>>(
      Types.newParameterizedType(Request::class.java, inputType)
    )
    val inputString = inputAdapter.toJson(Request(input))
    val response = queryOpa(document, inputString)
    return parseResponse(document, returnType, response)
  }

  /**
   * Evaluate / Query a document with given input of raw JSON.
   * This will connect to OPA via a retrofit interface and perform a /v1/data/{document} POST.
   * This consumes raw JSON for corner cases where developers need to do queries the automatic
   * JSON serialization doesn't support.
   *
   * @param document Name or Path of the OPA document to query.
   * @param input Input data to be supplied to OPA at evaluation time. Must be valid JSON.
   * @param returnType Return shape to be JSONified from OPA.
   * @throws PolicyEngineException if the request to OPA failed or the response shape didn't match R.
   * @throws IllegalArgumentException if no document path was specified.
   * @return Response shape R from OPA.
   */
  override fun <R: OpaResponse> evaluateRawJsonInput(
    document: String,
    input: String,
    returnType: Class<R>
  ): R {
    if (document.isEmpty()) {
      throw IllegalArgumentException("Must specify document")
    }
    val response = queryOpa(document, input)
    return parseResponse(document, returnType, response)
  }

  /**
   * Evaluate / Query a document with no additional input.
   * This will connect to OPA via a retrofit interface and perform a /v1/data/{document} POST.
   *
   * @param document Name or Path of the OPA document to query.
   * @param returnType Shape of response to be demarshelled from OPA
   * @throws PolicyEngineException if the request to OPA failed or the response shape didn't match R.
   * @throws IllegalArgumentException if no document path was specified.
   * @return Response shape R from OPA.
   */
  override fun <R : OpaResponse> evaluateNoInput(
    document: String,
    returnType: Class<R>
  ): R {
    return evaluateInternal(document, returnType)
  }

  private fun <R : OpaResponse> evaluateInternal(
    document: String,
    returnType: Class<R>
  ): R {
    val response = queryOpa(document, "")
    return parseResponse(document, returnType, response)
  }

  private fun queryOpa(
    document: String,
    inputString: String = ""
  ): retrofit2.Response<ResponseBody> {
    if (document.isEmpty()) {
      throw IllegalArgumentException("Must specify document")
    }

    val response = opaApi.queryDocument(document, inputString, config.provenance, config.metrics).execute()
    if (config.metrics) {
      metrics.evaluated(document)
    }

    if (!response.isSuccessful) {
      throw PolicyEngineException("[${response.code()}]: ${response.errorBody()?.string()}")
    }
    return response
  }

  private fun <R : OpaResponse> parseResponse(
    document: String,
    returnType: Class<R>,
    response: retrofit2.Response<ResponseBody>
  ): R {
    val outputAdapter = moshi.adapter<Response<R>>(
      Types.newParameterizedType(Response::class.java, returnType)
    )

    val responseBody =
      response.body()?.string() ?: throw PolicyEngineException("OPA response body is empty")
    val extractedResponse = try {
      outputAdapter.fromJson(responseBody)
        ?: throw PolicyEngineException("Unmarshalled OPA response body is empty")
    } catch (e: Exception) {
      throw PolicyEngineException("Response shape did not match", e)
    }

    if (extractedResponse.result == null) {
      throw PolicyEngineException("Policy document \"$document\" not found.")
    }
    extractedResponse.result.provenance = extractedResponse.provenance
    if (config.metrics && extractedResponse.metrics != null) {
      extractedResponse.result.metrics = extractedResponse.metrics
      metrics.observe(document, extractedResponse.result)
    }

    return extractedResponse.result
  }
}

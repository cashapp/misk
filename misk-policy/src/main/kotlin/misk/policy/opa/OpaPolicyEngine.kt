package misk.policy.opa

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.ResponseBody
import javax.inject.Inject
import javax.inject.Named

/**
 * Support for the Open Policy Engine (OPA).
 * OPA provides a means to decouple policy from business logic. The resulting query response may
 * have arbitrary shapes.
 */
class OpaPolicyEngine @Inject constructor(
  private val opaApi: OpaApi,
  @Named("opa-moshi") private val moshi: Moshi
) {

  /**
   * Evaluate / Query a document with given input of shape T.
   * This will connect to OPA via a retrofit interface and perform a /v1/data/{document} POST.
   *
   * @param document Name or Path of the OPA document to query.
   * @param input Input data to be supplied to OPA at evaluation time (the input global field).
   * @throws PolicyEngineException if the request to OPA failed or the response shape didn't match R.
   * @throws IllegalArgumentException if no document path was specified.
   * @return Response shape R from OPA.
   */
  inline fun <reified T, reified R> evaluate(document: String, input: T): R {
    return evaluate(document, input, T::class.java, R::class.java)
  }

  fun <T, R> evaluate(
    document: String,
    input: T,
    inputType: Class<T>,
    returnType: Class<R>
  ): R {
    return evaluateInternal(document, input, inputType, returnType)
  }

  private fun <T, R> evaluateInternal(
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
   * Evaluate / Query a document with no additional input.
   * This will connect to OPA via a retrofit interface and perform a /v1/data/{document} POST.
   *
   * @param document Name or Path of the OPA document to query.
   * @throws PolicyEngineException if the request to OPA failed or the response shape didn't match R.
   * @throws IllegalArgumentException if no document path was specified.
   * @return Response shape R from OPA.
   */
  inline fun <reified R> evaluate(document: String): R {
    return evaluate(document, R::class.java)
  }

  fun <R> evaluate(document: String, returnType: Class<R>): R {
    return evaluateInternal(document, returnType)
  }

  private fun <R> evaluateInternal(document: String, returnType: Class<R>): R {
    val response = queryOpa(document)
    return parseResponse(document, returnType, response)
  }

  private fun queryOpa(
    document: String,
    inputString: String = ""
  ): retrofit2.Response<ResponseBody> {
    if (document.isEmpty()) {
      throw IllegalArgumentException("Must specify document")
    }

    val response = opaApi.queryDocument(document, inputString).execute()
    if (!response.isSuccessful) {
      throw PolicyEngineException("[${response.code()}]: ${response.errorBody()?.string()}")
    }
    return response
  }

  private fun <R> parseResponse(
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

    return extractedResponse.result
  }
}

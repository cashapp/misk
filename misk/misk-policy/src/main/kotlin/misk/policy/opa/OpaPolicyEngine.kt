package misk.policy.opa

interface OpaPolicyEngine {
  fun <T : OpaRequest, R : OpaResponse> evaluateWithInput(
    document: String,
    input: T,
    inputType: Class<T>,
    returnType: Class<R>
  ): R

  fun <R : OpaResponse> evaluateNoInput(document: String, returnType: Class<R>): R
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
inline fun <reified R : OpaResponse> OpaPolicyEngine.evaluate(document: String): R {
  return evaluateNoInput(document, R::class.java)
}

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
inline fun <reified T : OpaRequest, reified R : OpaResponse> OpaPolicyEngine.evaluate(
  document: String,
  input: T
): R {
  return evaluateWithInput(document, input, T::class.java, R::class.java)
}

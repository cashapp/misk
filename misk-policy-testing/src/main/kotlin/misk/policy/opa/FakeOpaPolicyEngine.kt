package misk.policy.opa

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeOpaPolicyEngine @Inject constructor(): OpaPolicyEngine {
  override fun <T : OpaRequest, R : OpaResponse> evaluateWithInput(
    document: String,
    input: T,
    inputType: Class<T>,
    returnType: Class<R>
  ): R {
    val opaResponse = responsesForInput[document]?.get(input)
      ?: throw IllegalStateException("No override for document '$document' and input '$input'")

    @Suppress("UNCHECKED_CAST")
    return opaResponse as R
  }

  override fun <R: OpaResponse> evaluateRawJsonInput(
    document: String,
    input: String,
    returnType: Class<R>
  ): R {
    val opaResponse = responsesForJsonInput[document]?.get(input)
      ?: throw IllegalStateException("No override for document '$document' and input '$input'")

    @Suppress("UNCHECKED_CAST")
    return opaResponse as R
  }

  override fun <R : OpaResponse> evaluateNoInput(
    document: String,
    returnType: Class<R>
  ): R {
    val opaResponse = responses[document]
      ?: throw IllegalStateException("No override for document '$document'")

    @Suppress("UNCHECKED_CAST")
    return opaResponse as R
  }

  private val responses = mutableMapOf<String, OpaResponse>()
  fun addOverride(document: String, obj: OpaResponse) {
    responses[document] = obj
  }

  private val responsesForJsonInput = mutableMapOf<String, MutableMap<String,OpaResponse>>()
  fun addOverrideForInput(document: String, key: String, obj: OpaResponse) {
    if (responsesForJsonInput.containsKey(document)) {
      responsesForJsonInput[document]?.put(key,obj)
    } else {
      responsesForJsonInput[document] = mutableMapOf(Pair(key, obj))
    }
  }

  private val responsesForInput = mutableMapOf<String, MutableMap<OpaRequest,OpaResponse>>()
  fun addOverrideForInput(document: String, key: OpaRequest, obj: OpaResponse) {
    if(responsesForInput.containsKey(document)) {
      responsesForInput[document]?.put(key,obj)
    } else {
      responsesForInput[document] = mutableMapOf(Pair(key, obj))
    }
  }
}

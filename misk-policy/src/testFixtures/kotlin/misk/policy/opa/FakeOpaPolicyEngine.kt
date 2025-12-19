package misk.policy.opa

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.testing.FakeFixture

@Singleton
class FakeOpaPolicyEngine @Inject constructor() : OpaPolicyEngine, FakeFixture() {
  override fun <T : OpaRequest, R : OpaResponse> evaluateWithInput(
    document: String,
    input: T,
    inputType: Class<T>,
    returnType: Class<R>,
  ): R {
    val opaResponse =
      responsesForInput[document]?.get(input)
        ?: throw IllegalStateException("No override for document '$document' and input '$input'")

    @Suppress("UNCHECKED_CAST")
    return opaResponse as R
  }

  override fun <R : OpaResponse> evaluateRawJsonInput(document: String, input: String, returnType: Class<R>): R {
    val opaResponse =
      responsesForJsonInput[document]?.get(input)
        ?: throw IllegalStateException("No override for document '$document' and input '$input'")

    @Suppress("UNCHECKED_CAST")
    return opaResponse as R
  }

  override fun <R : OpaResponse> evaluateNoInput(document: String, returnType: Class<R>): R {
    val opaResponse = responses[document] ?: throw IllegalStateException("No override for document '$document'")

    @Suppress("UNCHECKED_CAST")
    return opaResponse as R
  }

  private val responses by resettable { mutableMapOf<String, OpaResponse>() }

  fun addOverride(document: String, obj: OpaResponse) {
    responses[document] = obj
  }

  private val responsesForJsonInput by resettable { mutableMapOf<String, MutableMap<String, OpaResponse>>() }

  fun addOverrideForInput(document: String, key: String, obj: OpaResponse) {
    if (responsesForJsonInput.containsKey(document)) {
      responsesForJsonInput[document]?.put(key, obj)
    } else {
      responsesForJsonInput[document] = mutableMapOf(Pair(key, obj))
    }
  }

  private val responsesForInput by resettable { mutableMapOf<String, MutableMap<OpaRequest, OpaResponse>>() }

  fun addOverrideForInput(document: String, key: OpaRequest, obj: OpaResponse) {
    if (responsesForInput.containsKey(document)) {
      responsesForInput[document]?.put(key, obj)
    } else {
      responsesForInput[document] = mutableMapOf(Pair(key, obj))
    }
  }
}

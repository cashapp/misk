package misk.mcp

import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import misk.annotation.ExperimentalMiskApi
import misk.mcp.internal.McpEra
import misk.mcp.internal.McpRequestContext
import misk.mcp.protocol.BooleanSchema
import misk.mcp.protocol.ElicitRequest
import misk.mcp.protocol.ElicitRequestFormParams
import misk.mcp.protocol.ElicitRequestParams
import misk.mcp.protocol.ElicitRequestURLParams
import misk.mcp.protocol.ElicitResult
import misk.mcp.protocol.InputRequest
import misk.mcp.protocol.InputRequiredResult
import misk.mcp.protocol.IntegerSchema
import misk.mcp.protocol.McpException
import misk.mcp.protocol.McpMethod
import misk.mcp.protocol.NumberSchema
import misk.mcp.protocol.PrimitiveSchemaDefinition
import misk.mcp.protocol.RPCError
import misk.mcp.protocol.RequestedSchema
import misk.mcp.protocol.StringSchema
import misk.mcp.protocol.UntitledSingleSelectEnumSchema

/*
 * Multi round-trip requests (MRTR): a tool, prompt or resource asks the client for more input by ending the request
 * with inputRequired(). The client collects the input, usually from the user, and sends the same request again with
 * the responses, which the handler reads with inputResponse():
 *
 * ```kotlin
 * override suspend fun handle(input: Input): ToolResult {
 *   val answer = inputResponse<Nickname>("nickname") ?: inputRequired {
 *     elicit<Nickname>("nickname", "What should I call you?")
 *   }
 *   return when (answer.action) {
 *     ElicitResult.Action.Accept -> ToolResult(TextContent("Hello, ${answer.content!!.nickname}!"))
 *     else -> ToolResult(TextContent("Hello!"))
 *   }
 * }
 * ```
 *
 * Each round runs the handler from the start, so work done before inputRequired() runs again. To carry results
 * between rounds, pass a state to inputRequired() and read it back with requestState(). The server signs the state;
 * see McpServerConfig.request_state_key.
 *
 * Only tools/call, prompts/get and resources/read can require input, and only from MCP 2026-07-28 clients. For older
 * clients, a tool call returns an error result and a prompt or resource request a -32021 error.
 */

/** The user's response to an elicitation. [content] is set only when the user accepted a form. */
@ExperimentalMiskApi data class ElicitResponse<T : Any>(val action: ElicitResult.Action, val content: T?)

/** The input requests of an [inputRequired] round, by key. */
@ExperimentalMiskApi
class InputRequests @PublishedApi internal constructor() {
  @PublishedApi internal val requests: MutableMap<String, InputRequest> = linkedMapOf()

  /** Asks the user to fill in a form whose fields are [T]'s properties: strings, numbers, booleans and enums. */
  inline fun <reified T : Any> elicit(key: String, message: String) {
    elicit(key, ElicitRequestFormParams(message = message, requestedSchema = typeOf<T>().toRequestedSchema()))
  }

  /** Sends the user to [url], for input that must not pass through the client, such as a credential. */
  fun elicitUrl(key: String, message: String, url: String) {
    elicit(key, ElicitRequestURLParams(message = message, url = url))
  }

  fun elicit(key: String, params: ElicitRequestParams) {
    requests[key] = ElicitRequest(params)
  }
}

/**
 * Returns the client's response to the input request [key] of the previous round, or null when there is none, as on the
 * first round.
 *
 * @throws McpException with `-32602` when the response doesn't decode as an elicitation result with [T] content.
 */
@ExperimentalMiskApi
suspend inline fun <reified T : Any> inputResponse(key: String): ElicitResponse<T>? =
  decodeInputResponse(key, serializer<T>())

@PublishedApi
@OptIn(ExperimentalMiskApi::class)
internal suspend fun <T : Any> decodeInputResponse(key: String, deserializer: KSerializer<T>): ElicitResponse<T>? {
  val response = currentCoroutineContext()[McpRequestContext]?.inputResponses?.get(key) ?: return null
  return try {
    val result = McpJson.decodeFromJsonElement(ElicitResult.serializer(), response)
    val content = result.content?.takeIf { result.action == ElicitResult.Action.Accept }
    ElicitResponse(result.action, content?.let { McpJson.decodeFromJsonElement(deserializer, it) })
  } catch (e: SerializationException) {
    throw McpException(RPCError.ErrorCode.INVALID_PARAMS, "Invalid input response '$key': ${e.message}")
  } catch (e: IllegalArgumentException) {
    throw McpException(RPCError.ErrorCode.INVALID_PARAMS, "Invalid input response '$key': ${e.message}")
  }
}

/** Returns the state passed to [inputRequired] in the previous round, or null on the first round. */
@ExperimentalMiskApi
suspend inline fun <reified S : Any> requestState(): S? =
  requestStateJson()?.let { McpJson.decodeFromJsonElement(serializer<S>(), it) }

@PublishedApi
@OptIn(ExperimentalMiskApi::class)
internal suspend fun requestStateJson(): JsonElement? = currentCoroutineContext()[McpRequestContext]?.requestState

/**
 * Ends the current request with an `input_required` result. The client fulfills the input requests that [block] adds
 * and retries the request, so this never returns.
 *
 * It ends the request by throwing. A handler that catches the exception still ends with the input request, whatever it
 * returns or throws instead. Its `catch (e: Exception)` block and the code after it still run, though, and their result
 * is discarded, so keep side effects such as error logs and failure metrics out of them. Catch narrower exceptions
 * where you can.
 */
@ExperimentalMiskApi suspend fun inputRequired(block: InputRequests.() -> Unit): Nothing = requireInput(null, block)

/**
 * Like [inputRequired], and also hands [state] to the next round, where [requestState] returns it. The client can't
 * read or change the state, and it expires after 10 minutes.
 */
@ExperimentalMiskApi
suspend inline fun <reified S : Any> inputRequired(state: S, noinline block: InputRequests.() -> Unit = {}): Nothing =
  requireInput(McpJson.encodeToJsonElement(serializer<S>(), state), block)

@PublishedApi
@OptIn(ExperimentalMiskApi::class)
internal suspend fun requireInput(state: JsonElement?, block: InputRequests.() -> Unit): Nothing {
  val context =
    currentCoroutineContext()[McpRequestContext] ?: error("inputRequired() must be called from an MCP handler")
  check(context.request.method in INPUT_METHODS) {
    "Only tools/call, prompts/get and resources/read can require input, not ${context.request.method}"
  }
  if (context.era == McpEra.Legacy) throw LegacyInputRequiredException().also { context.inputSignal = it }

  val requests = InputRequests().apply(block).requests.toMap()
  checkElicitationCapabilities(context, requests.values)
  val requestState =
    state?.let {
      val codec =
        checkNotNull(context.stream.server.requestStateCodec) {
          "Set request_state_key in the config of MCP server ${context.stream.server.name} to pass a requestState"
        }
      codec.seal(it, context.binding)
    }
  throw InputRequiredException(
      InputRequiredResult(inputRequests = requests.ifEmpty { null }, requestState = requestState)
    )
    .also { context.inputSignal = it }
}

private fun checkElicitationCapabilities(context: McpRequestContext, requests: Collection<InputRequest>) {
  val elicitation = context.meta?.clientCapabilities?.elicitation
  val modes = requests.filterIsInstance<ElicitRequest>().map { it.params.mode }.toSet()
  val missing =
    modes.filter { mode ->
      when (mode) {
        ElicitRequestFormParams.MODE -> elicitation?.supportsForm != true
        else -> elicitation?.supportsUrl != true
      }
    }
  if (missing.isNotEmpty()) {
    throw McpException(
      code = RPCError.ErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY,
      message = "The client must support elicitation (${missing.joinToString()})",
      data =
        JsonObject(
          mapOf(
            "requiredCapabilities" to
              JsonObject(mapOf("elicitation" to JsonObject(missing.associateWith { JsonObject(emptyMap()) })))
          )
        ),
    )
  }
}

private val INPUT_METHODS = setOf(McpMethod.TOOLS_CALL, McpMethod.PROMPTS_GET, McpMethod.RESOURCES_READ)

/** Carries an [InputRequiredResult] from [inputRequired] to the dispatcher. */
internal class InputRequiredException(val result: InputRequiredResult) : RuntimeException() {
  override fun fillInStackTrace(): Throwable = this
}

/** Thrown by [inputRequired] for a client that predates MCP 2026-07-28. */
internal class LegacyInputRequiredException :
  RuntimeException("This request needs more input, which requires a client that supports MCP 2026-07-28")

/** Builds an elicitation form schema from the properties of a serializable class. */
@PublishedApi
@OptIn(ExperimentalSerializationApi::class)
internal fun KType.toRequestedSchema(): RequestedSchema {
  val descriptor = serializer().descriptor
  val properties = linkedMapOf<String, PrimitiveSchemaDefinition>()
  val required = mutableListOf<String>()

  for (index in 0 until descriptor.elementsCount) {
    val name = descriptor.getElementName(index)
    val element = descriptor.getElementDescriptor(index)
    val description = descriptor.getElementAnnotations(index).filterIsInstance<Description>().firstOrNull()?.value

    properties[name] =
      when (element.kind) {
        SerialKind.ENUM ->
          UntitledSingleSelectEnumSchema(
            enumValues = (0 until element.elementsCount).map { element.getElementName(it) },
            description = description,
          )
        PrimitiveKind.INT,
        PrimitiveKind.LONG,
        PrimitiveKind.SHORT,
        PrimitiveKind.BYTE -> IntegerSchema(description = description)
        PrimitiveKind.FLOAT,
        PrimitiveKind.DOUBLE -> NumberSchema(description = description)
        PrimitiveKind.BOOLEAN -> BooleanSchema(description = description)
        else -> StringSchema(description = description)
      }
    if (!descriptor.isElementOptional(index) && !element.isNullable) required += name
  }
  return RequestedSchema(properties = properties, required = required.ifEmpty { null })
}

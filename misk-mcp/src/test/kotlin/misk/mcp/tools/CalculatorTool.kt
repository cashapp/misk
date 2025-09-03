@file:Suppress("PropertyName", "LocalVariableName")

package misk.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import jakarta.inject.Inject
import kotlinx.serialization.Serializable
import misk.annotation.ExperimentalMiskApi
import misk.mcp.StructuredMcpTool
import misk.mcp.tools.CalculatorToolInput.Operation

@Serializable
data class CalculatorToolInput(
  @misk.mcp.Description(
    """operation to perform, should be one of ADD, SUBTRACT, MULTIPLY, DIVIDE.
    | An ADD operation should be chosen for PLUS or (+) operator
    | A SUBTRACT operation should be chosen for MINUS, or (-)  operator
    | A MULTIPLY operation should be chosen for TIMES, or (x, *)  operator
    | A DIVIDE operation should be chosen for DIVIDED BY, or (/)  operator
  """,
  )
  val operation: String,
  @misk.mcp.Description("first term, an integer")
  val first_term: Int,
  @misk.mcp.Description("second term, an integer")
  val second_term: Int,
) {
  enum class Operation() { ADD, SUBTRACT, MULTIPLY, DIVIDE }
}

@Serializable
data class CalculatorToolOutput(
  @misk.mcp.Description(
    """result of the operation
      A SUM for ADD,
      A DIFFERENCE for SUBTRACT,
      A PRODUCT for MULTIPLY,
      A QUOTIENT and REMAINDER for DIVIDE operations
  """,
  )
  val result: Int,
  @misk.mcp.Description(
    """optional remainder of the operation, only present for DIVIDE operations that do not divide evenly
      For example, if the operation is 7 divided by 3, the result would be 2 and the remainder would be 1
      If the operation is 8 divided by 4, the result would be 2 and the remainder would be null
  """,
  )
  val remainder: Int? = null,
)

@OptIn(ExperimentalMiskApi::class)
class CalculatorTool @Inject constructor() : StructuredMcpTool<CalculatorToolInput, CalculatorToolOutput>() {
  override val name = "calculator"
  override val description =
    """A simple calculator that can perform basic arithmetic operations such as 
      |addition, subtraction, multiplication, and division on two integers. In the input, 
      |the operation will be interpreted as `firstTerm <operation> secondTerm`. 
      |""".trimMargin()

  override suspend fun handle(input: CalculatorToolInput): ToolResult {
    val operation = Operation.valueOf(input.operation.uppercase())
    return when (operation) {
      Operation.ADD ->
        ToolResult(
          CalculatorToolOutput(
            result = input.first_term + input.second_term,
          ),
        )

      Operation.SUBTRACT ->
        ToolResult(
          CalculatorToolOutput(
            result = input.first_term - input.second_term,
          ),
        )

      Operation.MULTIPLY ->
        ToolResult(
          CalculatorToolOutput(
            result = input.first_term * input.second_term,
          ),
        )

      Operation.DIVIDE ->
        if (input.second_term != 0) {
          ToolResult(
            CalculatorToolOutput(
              result = input.first_term / input.second_term,
              remainder = (input.first_term % input.second_term).let { if (it == 0) null else it },
            ),
          )
        } else {
          ToolResult(
            TextContent("Division by zero is not allowed."),
            isError = true,
          )
        }
    }
  }

}

suspend fun Client.callCalculatorTool(
  first_term: Int,
  second_term: Int,
  operation: Operation,
) = callTool(
  name = "calculator",
  arguments = mapOf(
    "operation" to operation.name,
    "first_term" to first_term,
    "second_term" to second_term,
  ),
)

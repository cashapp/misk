@file:OptIn(ExperimentalMiskApi::class)

package misk.mcp

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import misk.annotation.ExperimentalMiskApi
import misk.mcp.testing.tools.CalculatorTool
import misk.mcp.testing.tools.CalculatorToolInput
import misk.mcp.testing.tools.CalculatorToolOutput
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ToolResultExtensionsTest {

  @Test
  fun `result() extracts typed result from StructuredToolResult`() = runTest {
    val tool = CalculatorTool()
    val toolResult = tool.handle(CalculatorToolInput("DIVIDE", 7, 3))

    val output: CalculatorToolOutput = toolResult.result()

    assertEquals(2, output.result)
    assertEquals(1, output.remainder)
  }

  @Test
  fun `result() throws IllegalStateException for PromptToolResult`() = runTest {
    val tool = CalculatorTool()
    // Division by zero returns a PromptToolResult with isError = true
    val toolResult = tool.handle(CalculatorToolInput("DIVIDE", 5, 0))

    val exception = assertThrows<IllegalStateException> { toolResult.result<CalculatorToolOutput>() }

    assertNotNull(exception.message)
    assertEquals(
      "Expected StructuredToolResult with type class misk.mcp.testing.tools.CalculatorToolOutput, but got PromptToolResult",
      exception.message,
    )
  }

  @Test
  fun `result() throws IllegalStateException for type mismatch`() = runTest {
    val tool = CalculatorTool()
    val toolResult = tool.handle(CalculatorToolInput("ADD", 5, 3))

    val exception =
      assertThrows<IllegalStateException> {
        // Try to extract as wrong type
        toolResult.result<String>()
      }

    assertNotNull(exception.message)
    assertEquals(
      "Expected result of type class kotlin.String, but got class misk.mcp.testing.tools.CalculatorToolOutput",
      exception.message,
    )
  }
}

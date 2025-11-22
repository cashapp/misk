package misk.mcp.internal

import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import misk.mcp.McpJson
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.fail

/**
 * Test to verify that the schemas produced by JSON Schema extensions correctly describe serialized data.
 */
class JsonSchemaExtensionsIntegrationTest {
  @Serializable
  data class SampleData(
    private val id: Int,
    private val name: String,
    private val nullableName: String?,
    private val optionalName: String = "hello",
    private val map: Map<String, String>,
    private val recursive: List<SampleData>,
    private val enum: SampleEnum,
    private val jsonObject: JsonObject,
    private val sealedFruit: List<Fruit>,
  ) {
    enum class SampleEnum {
      FIRST, SECOND, THIRD
    }

    @Serializable
    sealed interface Fruit

    @Serializable
    data class Apple(val variety: String, val radius: Int): Fruit

    @Serializable
    data class Banana(val length: Int, val ripeness: String): Fruit
  }

  @Test
  fun `verify generated schema is valid`() {
    val sampleData = SampleData(
      id = 100,
      name = "Test String",
      nullableName = null,
      map = mapOf("foo" to "bar"),
      recursive = emptyList(),
      enum = SampleData.SampleEnum.FIRST,
      jsonObject = buildJsonObject {
        put("foo", JsonPrimitive("bar"))
      },
      sealedFruit = listOf(
        SampleData.Apple(variety = "Granny Smith", radius = 5),
        SampleData.Banana(length = 7, ripeness = "Ripe"),
      ),
    )
    val schemaJsonString = McpJson.encodeToString(generateJsonSchema<SampleData>())
    val schema = SCHEMA_REGISTRY.getSchema(schemaJsonString)
    val sampleDataSerialized = McpJson.encodeToString(sampleData)

    val errors = schema.validate(sampleDataSerialized, InputFormat.JSON)
    if (errors.isNotEmpty()) {
      fail(buildString {
        appendLine("There were schema validation errors associated with the schema produced by the JsonSchemaExtensions.")
        appendLine("Errors:")
        errors.forEach { appendLine("- $it") }
        appendLine()
        appendLine("Serialized Data:")
        appendLine(sampleDataSerialized)
        appendLine()
        appendLine("Schema:")
        appendLine(schema)
      })
    }

    assertContentEquals(emptyList(), errors, "Schema validation errors: $errors")
  }

  companion object {
    private val SCHEMA_REGISTRY = SchemaRegistry.builder()
      .defaultDialectId(SpecificationVersion.DRAFT_2020_12.dialectId)
      .build()
  }
}

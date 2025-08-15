package misk.mcp.internal

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import misk.mcp.Description
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class JsonSchemaExtensionsTest {

  // Test data classes for primitive values
  data class PrimitiveObject(
    val stringField: String,
    val intField: Int,
    val longField: Long,
    val floatField: Float,
    val doubleField: Double,
    val booleanField: Boolean
  )

  // Test data classes for nullable and optional fields
  data class NullableOptionalObject(
    val requiredString: String,
    val nullableString: String?,
    val optionalString: String = "default",
    val optionalNullableString: String? = null
  )

  // Test data classes for embedded objects
  data class Address(
    val street: String,
    val city: String,
    val zipCode: Int
  )

  data class Person(
    val name: String,
    val age: Int,
    val address: Address
  )

  // Test data classes for nested objects
  data class Company(
    val name: String,
    val address: Address,
    val ceo: Person
  )

  // Test data classes for arrays/lists
  data class ArrayObject(
    val stringList: List<String>,
    val intList: List<Int>,
    val objectList: List<Address>
  )

  // Test data class with mixed types but no arrays (to test working functionality)
  data class SimpleComplexObject(
    val id: Long,
    val name: String,
    val active: Boolean,
    val metadata: Address,
    val score: Double?,
    val description: String = "default description"
  )

  // Test data classes for Description annotation
  data class DescribedObject(
    @Description("The unique identifier for this object")
    val id: Long,
    @Description("The display name of the object")
    val name: String,
    @Description("Whether this object is currently active")
    val active: Boolean,
    @Description("Optional metadata for the object")
    val metadata: String? = null,
    val undescribedField: String
  )

  // Test data classes for maps
  data class MapObject(
    val stringToStringMap: Map<String, String>,
    val stringToIntMap: Map<String, Int>,
    val stringToObjectMap: Map<String, Address>,
    @Description("A map of configuration values")
    val configMap: Map<String, String>
  )

  data class NestedMapObject(
    val mapOfMaps: Map<String, Map<String, String>>,
    @Description("A complex nested structure")
    val complexMap: Map<String, Map<String, Address>>
  )

  data class MixedDescribedObject(
    @Description("User identification number")
    val userId: Long,
    @Description("User profile information")
    val profile: Person,
    @Description("User preferences as key-value pairs")
    val preferences: Map<String, String>,
    @Description("User's addresses mapped by type (home, work, etc.)")
    val addresses: Map<String, Address>,
    val tags: List<String>
  )

  // Test data classes for single field and field names
  data class SingleFieldObject(val value: String)

  data class FieldNamesObject(
    val camelCase: String,
    val snake_case: String,
    val PascalCase: String,
    val `field with spaces`: String,
    val field123: String
  )

  // Test class without primary constructor
  class NoConstructorClass {
    constructor(value: String) // Secondary constructor only
  }

  @Test
  fun `generateJsonSchema handles primitive types correctly`() {
    val schema = PrimitiveObject::class.generateJsonSchema()

    // Verify top-level structure
    assertEquals(JsonPrimitive("object"), schema["type"])
    assertTrue(schema.containsKey("properties"))
    assertTrue(schema.containsKey("required"))

    val properties = schema["properties"] as JsonObject
    val required = schema["required"] as JsonArray

    // Verify all fields are present
    assertEquals(6, properties.size)
    assertEquals(6, required.size)

    // Verify string field
    val stringField = properties["stringField"] as JsonObject
    assertEquals(JsonPrimitive("string"), stringField["type"])

    // Verify int field
    val intField = properties["intField"] as JsonObject
    assertEquals(JsonPrimitive("integer"), intField["type"])

    // Verify long field
    val longField = properties["longField"] as JsonObject
    assertEquals(JsonPrimitive("integer"), longField["type"])

    // Verify float field
    val floatField = properties["floatField"] as JsonObject
    assertEquals(JsonPrimitive("number"), floatField["type"])

    // Verify double field
    val doubleField = properties["doubleField"] as JsonObject
    assertEquals(JsonPrimitive("number"), doubleField["type"])

    // Verify boolean field
    val booleanField = properties["booleanField"] as JsonObject
    assertEquals(JsonPrimitive("boolean"), booleanField["type"])

    // Verify all fields are required
    val requiredFields = required.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(
      setOf("stringField", "intField", "longField", "floatField", "doubleField", "booleanField"),
      requiredFields,
    )
  }

  @Test
  fun `generateJsonSchema handles nullable and optional fields correctly`() {
    val schema = NullableOptionalObject::class.generateJsonSchema()

    val properties = schema["properties"] as JsonObject
    val required = schema["required"] as JsonArray

    // Verify required field
    val requiredString = properties["requiredString"] as JsonObject
    assertEquals(JsonPrimitive("string"), requiredString["type"])
    assertEquals(null, requiredString["nullable"]) // Should not have nullable property

    // Verify nullable field
    val nullableString = properties["nullableString"] as JsonObject
    assertEquals(JsonPrimitive("string"), nullableString["type"])

    // Verify optional field (has default value)
    val optionalString = properties["optionalString"] as JsonObject
    assertEquals(JsonPrimitive("string"), optionalString["type"])
    assertEquals(null, optionalString["nullable"]) // Should not have nullable property

    // Verify optional nullable field
    val optionalNullableString = properties["optionalNullableString"] as JsonObject
    assertEquals(JsonPrimitive("string"), optionalNullableString["type"])

    // Verify required fields - only requiredString should be required
    val requiredFields = required.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("requiredString"), requiredFields)
  }

  @Test
  fun `generateJsonSchema handles embedded objects correctly`() {
    val schema = Person::class.generateJsonSchema()

    val properties = schema["properties"] as JsonObject
    val required = schema["required"] as JsonArray

    // Verify primitive fields
    assertEquals(JsonPrimitive("string"), (properties["name"] as JsonObject)["type"])
    assertEquals(JsonPrimitive("integer"), (properties["age"] as JsonObject)["type"])

    // Verify embedded object
    val addressField = properties["address"] as JsonObject
    assertEquals(JsonPrimitive("object"), addressField["type"])
    assertTrue(addressField.containsKey("properties"))
    // Note: Nested objects don't have "required" field due to level check in implementation
    assertFalse(addressField.containsKey("required"))

    val addressProperties = addressField["properties"] as JsonObject

    // Verify address properties
    assertEquals(JsonPrimitive("string"), (addressProperties["street"] as JsonObject)["type"])
    assertEquals(JsonPrimitive("string"), (addressProperties["city"] as JsonObject)["type"])
    assertEquals(JsonPrimitive("integer"), (addressProperties["zipCode"] as JsonObject)["type"])

    // Verify all top-level fields are required
    val requiredFields = required.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("name", "age", "address"), requiredFields)
  }

  @Test
  fun `generateJsonSchema handles deeply nested objects correctly`() {
    val schema = Company::class.generateJsonSchema()

    val properties = schema["properties"] as JsonObject

    // Verify company name
    assertEquals(JsonPrimitive("string"), (properties["name"] as JsonObject)["type"])

    // Verify address field (embedded object)
    val addressField = properties["address"] as JsonObject
    assertEquals(JsonPrimitive("object"), addressField["type"])

    // Verify CEO field (embedded object with nested object)
    val ceoField = properties["ceo"] as JsonObject
    assertEquals(JsonPrimitive("object"), ceoField["type"])

    val ceoProperties = ceoField["properties"] as JsonObject
    assertEquals(JsonPrimitive("string"), (ceoProperties["name"] as JsonObject)["type"])
    assertEquals(JsonPrimitive("integer"), (ceoProperties["age"] as JsonObject)["type"])

    // Verify CEO's address (nested object within nested object)
    val ceoAddressField = ceoProperties["address"] as JsonObject
    assertEquals(JsonPrimitive("object"), ceoAddressField["type"])

    val ceoAddressProperties = ceoAddressField["properties"] as JsonObject
    assertEquals(JsonPrimitive("string"), (ceoAddressProperties["street"] as JsonObject)["type"])
    assertEquals(JsonPrimitive("string"), (ceoAddressProperties["city"] as JsonObject)["type"])
    assertEquals(JsonPrimitive("integer"), (ceoAddressProperties["zipCode"] as JsonObject)["type"])
  }

  @Test
  fun `generateJsonSchema handles complex mixed object without arrays correctly`() {
    val schema = SimpleComplexObject::class.generateJsonSchema()

    val properties = schema["properties"] as JsonObject
    val required = schema["required"] as JsonArray

    // Verify all field types
    assertEquals(JsonPrimitive("integer"), (properties["id"] as JsonObject)["type"])
    assertEquals(JsonPrimitive("string"), (properties["name"] as JsonObject)["type"])
    assertEquals(JsonPrimitive("boolean"), (properties["active"] as JsonObject)["type"])
    assertEquals(JsonPrimitive("object"), (properties["metadata"] as JsonObject)["type"])

    // Verify nullable field
    val scoreField = properties["score"] as JsonObject
    assertEquals(JsonPrimitive("number"), scoreField["type"])

    // Verify optional field
    val descriptionField = properties["description"] as JsonObject
    assertEquals(JsonPrimitive("string"), descriptionField["type"])
    assertEquals(null, descriptionField["nullable"])

    // Verify required fields (should not include optional description)
    val requiredFields = required.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("id", "name", "active", "metadata"), requiredFields)

    // Verify nested object structure
    val metadataField = properties["metadata"] as JsonObject
    val metadataProperties = metadataField["properties"] as JsonObject
    assertEquals(3, metadataProperties.size) // street, city, zipCode
  }

  @Test
  fun `generateJsonSchema fails gracefully for class without primary constructor`() {
    assertFailsWith<IllegalArgumentException> {
      NoConstructorClass::class.generateJsonSchema()
    }
  }

  @Test
  fun `generateJsonSchema handles single field object correctly`() {
    val schema = SingleFieldObject::class.generateJsonSchema()

    val properties = schema["properties"] as JsonObject
    val required = schema["required"] as JsonArray

    assertEquals(1, properties.size)
    assertEquals(1, required.size)

    assertEquals(JsonPrimitive("string"), (properties["value"] as JsonObject)["type"])
    assertEquals("value", (required[0] as JsonPrimitive).content)
  }

  @Test
  fun `generateJsonSchema preserves field names correctly`() {
    val schema = FieldNamesObject::class.generateJsonSchema()
    val properties = schema["properties"] as JsonObject

    assertTrue(properties.containsKey("camelCase"))
    assertTrue(properties.containsKey("snake_case"))
    assertTrue(properties.containsKey("PascalCase"))
    assertTrue(properties.containsKey("field with spaces"))
    assertTrue(properties.containsKey("field123"))
  }

  @Test
  fun `generateJsonSchema handles Description annotation correctly`() {
    val schema = DescribedObject::class.generateJsonSchema()
    val properties = schema["properties"] as JsonObject

    // Verify fields with descriptions have description property
    val idField = properties["id"] as JsonObject
    assertEquals(JsonPrimitive("integer"), idField["type"])
    assertEquals(JsonPrimitive("The unique identifier for this object"), idField["description"])

    val nameField = properties["name"] as JsonObject
    assertEquals(JsonPrimitive("string"), nameField["type"])
    assertEquals(JsonPrimitive("The display name of the object"), nameField["description"])

    val activeField = properties["active"] as JsonObject
    assertEquals(JsonPrimitive("boolean"), activeField["type"])
    assertEquals(JsonPrimitive("Whether this object is currently active"), activeField["description"])

    val metadataField = properties["metadata"] as JsonObject
    assertEquals(JsonPrimitive("string"), metadataField["type"])
    assertEquals(JsonPrimitive("Optional metadata for the object"), metadataField["description"])

    // Verify field without description annotation doesn't have description property
    val undescribedField = properties["undescribedField"] as JsonObject
    assertEquals(JsonPrimitive("string"), undescribedField["type"])
    assertFalse(undescribedField.containsKey("description"))
  }

  @Test
  fun `generateJsonSchema handles embedded maps correctly`() {
    val schema = MapObject::class.generateJsonSchema()
    val properties = schema["properties"] as JsonObject
    val required = schema["required"] as JsonArray

    // Verify string to string map
    val stringToStringMapField = properties["stringToStringMap"] as JsonObject
    assertEquals(JsonPrimitive("object"), stringToStringMapField["type"])
    assertTrue(stringToStringMapField.containsKey("additionalProperties"))
    val stringMapAdditionalProps = stringToStringMapField["additionalProperties"] as JsonObject
    assertEquals(JsonPrimitive("string"), stringMapAdditionalProps["type"])

    // Verify string to int map
    val stringToIntMapField = properties["stringToIntMap"] as JsonObject
    assertEquals(JsonPrimitive("object"), stringToIntMapField["type"])
    assertTrue(stringToIntMapField.containsKey("additionalProperties"))
    val intMapAdditionalProps = stringToIntMapField["additionalProperties"] as JsonObject
    assertEquals(JsonPrimitive("integer"), intMapAdditionalProps["type"])

    // Verify string to object map
    val stringToObjectMapField = properties["stringToObjectMap"] as JsonObject
    assertEquals(JsonPrimitive("object"), stringToObjectMapField["type"])
    assertTrue(stringToObjectMapField.containsKey("additionalProperties"))
    val objectMapAdditionalProps = stringToObjectMapField["additionalProperties"] as JsonObject
    assertEquals(JsonPrimitive("object"), objectMapAdditionalProps["type"])
    assertTrue(objectMapAdditionalProps.containsKey("properties"))

    // Verify nested object properties in map
    val nestedProperties = objectMapAdditionalProps["properties"] as JsonObject
    assertEquals(JsonPrimitive("string"), (nestedProperties["street"] as JsonObject)["type"])
    assertEquals(JsonPrimitive("string"), (nestedProperties["city"] as JsonObject)["type"])
    assertEquals(JsonPrimitive("integer"), (nestedProperties["zipCode"] as JsonObject)["type"])

    // Verify map with description
    val configMapField = properties["configMap"] as JsonObject
    assertEquals(JsonPrimitive("object"), configMapField["type"])
    assertEquals(JsonPrimitive("A map of configuration values"), configMapField["description"])
    assertTrue(configMapField.containsKey("additionalProperties"))
    val configMapAdditionalProps = configMapField["additionalProperties"] as JsonObject
    assertEquals(JsonPrimitive("string"), configMapAdditionalProps["type"])

    // Verify all fields are required
    val requiredFields = required.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("stringToStringMap", "stringToIntMap", "stringToObjectMap", "configMap"), requiredFields)
  }

  @Test
  fun `generateJsonSchema handles nested maps correctly`() {
    val schema = NestedMapObject::class.generateJsonSchema()
    val properties = schema["properties"] as JsonObject

    // Verify map of maps
    val mapOfMapsField = properties["mapOfMaps"] as JsonObject
    assertEquals(JsonPrimitive("object"), mapOfMapsField["type"])
    assertTrue(mapOfMapsField.containsKey("additionalProperties"))

    val outerMapAdditionalProps = mapOfMapsField["additionalProperties"] as JsonObject
    assertEquals(JsonPrimitive("object"), outerMapAdditionalProps["type"])
    assertTrue(outerMapAdditionalProps.containsKey("additionalProperties"))

    val innerMapAdditionalProps = outerMapAdditionalProps["additionalProperties"] as JsonObject
    assertEquals(JsonPrimitive("string"), innerMapAdditionalProps["type"])

    // Verify complex nested map with description
    val complexMapField = properties["complexMap"] as JsonObject
    assertEquals(JsonPrimitive("object"), complexMapField["type"])
    assertEquals(JsonPrimitive("A complex nested structure"), complexMapField["description"])
    assertTrue(complexMapField.containsKey("additionalProperties"))

    val complexOuterAdditionalProps = complexMapField["additionalProperties"] as JsonObject
    assertEquals(JsonPrimitive("object"), complexOuterAdditionalProps["type"])
    assertTrue(complexOuterAdditionalProps.containsKey("additionalProperties"))

    val complexInnerAdditionalProps = complexOuterAdditionalProps["additionalProperties"] as JsonObject
    assertEquals(JsonPrimitive("object"), complexInnerAdditionalProps["type"])
    assertTrue(complexInnerAdditionalProps.containsKey("properties"))

    // Verify the Address object properties in the nested map
    val addressProperties = complexInnerAdditionalProps["properties"] as JsonObject
    assertEquals(JsonPrimitive("string"), (addressProperties["street"] as JsonObject)["type"])
    assertEquals(JsonPrimitive("string"), (addressProperties["city"] as JsonObject)["type"])
    assertEquals(JsonPrimitive("integer"), (addressProperties["zipCode"] as JsonObject)["type"])
  }

  @Test
  fun `generateJsonSchema handles mixed maps and objects with descriptions`() {
    val schema = MixedDescribedObject::class.generateJsonSchema()
    val properties = schema["properties"] as JsonObject

    // Verify described primitive field
    val userIdField = properties["userId"] as JsonObject
    assertEquals(JsonPrimitive("integer"), userIdField["type"])
    assertEquals(JsonPrimitive("User identification number"), userIdField["description"])

    // Verify described object field
    val profileField = properties["profile"] as JsonObject
    assertEquals(JsonPrimitive("object"), profileField["type"])
    assertEquals(JsonPrimitive("User profile information"), profileField["description"])
    assertTrue(profileField.containsKey("properties"))

    // Verify described map field
    val preferencesField = properties["preferences"] as JsonObject
    assertEquals(JsonPrimitive("object"), preferencesField["type"])
    assertEquals(JsonPrimitive("User preferences as key-value pairs"), preferencesField["description"])
    assertTrue(preferencesField.containsKey("additionalProperties"))

    // Verify described map with object values
    val addressesField = properties["addresses"] as JsonObject
    assertEquals(JsonPrimitive("object"), addressesField["type"])
    assertEquals(JsonPrimitive("User's addresses mapped by type (home, work, etc.)"), addressesField["description"])
    assertTrue(addressesField.containsKey("additionalProperties"))

    val addressAdditionalProps = addressesField["additionalProperties"] as JsonObject
    assertEquals(JsonPrimitive("object"), addressAdditionalProps["type"])
    assertTrue(addressAdditionalProps.containsKey("properties"))

    // Verify undescribed array field
    val tagsField = properties["tags"] as JsonObject
    assertEquals(JsonPrimitive("array"), tagsField["type"])
    assertFalse(tagsField.containsKey("description"))
    assertTrue(tagsField.containsKey("items"))
  }
}

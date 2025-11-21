@file:Suppress("unused", "PropertyName")

package misk.mcp.internal

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
  @Serializable
  data class PrimitiveObject(
    val stringField: String,
    val intField: Int,
    val longField: Long,
    val floatField: Float,
    val doubleField: Double,
    val booleanField: Boolean
  )

  // Test data classes for nullable and optional fields
  @Serializable
  data class NullableOptionalObject(
    val requiredString: String,
    val nullableString: String?,
    val optionalString: String = "default",
    val optionalNullableString: String? = null
  )

  // Test data classes for embedded objects
  @Serializable
  data class Address(
    val street: String,
    val city: String,
    val zipCode: Int
  )

  @Serializable
  data class Person(
    val name: String,
    val age: Int,
    val address: Address
  )

  // Test data classes for nested objects
  @Serializable
  data class Company(
    val name: String,
    val address: Address,
    val ceo: Person
  )

  // Test data classes for arrays/lists
  @Serializable
  data class ArrayObject(
    val stringList: List<String>,
    val intList: List<Int>,
    val objectList: List<Address>
  )

  // Test data class with mixed types but no arrays (to test working functionality)
  @Serializable
  data class SimpleComplexObject(
    val id: Long,
    val name: String,
    val active: Boolean,
    val metadata: Address,
    val score: Double?,
    val description: String = "default description"
  )

  // Test data classes for Description annotation
  @Serializable
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
  @Serializable
  data class MapObject(
    val stringToStringMap: Map<String, String>,
    val stringToIntMap: Map<String, Int>,
    val stringToObjectMap: Map<String, Address>,
    @Description("A map of configuration values")
    val configMap: Map<String, String>
  )

  @Serializable
  data class NestedMapObject(
    val mapOfMaps: Map<String, Map<String, String>>,
    @Description("A complex nested structure")
    val complexMap: Map<String, Map<String, Address>>
  )

  @Serializable
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
  @Serializable
  data class SingleFieldObject(val value: String)

  @Serializable
  data class FieldNamesObject(
    val camelCase: String,
    val snake_case: String,
    val PascalCase: String,
    val `field with spaces`: String,
    val field123: String
  )

  // Test class without serializer
  class NoSerializerClass {
    constructor(value: String) // Secondary constructor only
  }

  // Test enums
  @Serializable
  enum class Status {
    ACTIVE,
    INACTIVE,
    PENDING,
    ARCHIVED
  }

  @Serializable
  enum class Priority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
  }

  // Test data classes with enums
  @Serializable
  data class EnumObject(
    val status: Status,
    val priority: Priority
  )

  @Serializable
  data class OptionalEnumObject(
    val status: Status,
    val priority: Priority?,
    val defaultStatus: Status = Status.ACTIVE
  )

  @Serializable
  data class DescribedEnumObject(
    @Description("The current status of the object")
    val status: Status,
    @Description("The priority level")
    val priority: Priority
  )

  @Serializable
  data class EnumListObject(
    val statuses: List<Status>,
    val priorities: List<Priority>
  )

  @Serializable
  data class EnumMapObject(
    val statusMap: Map<String, Status>,
    val priorityMap: Map<String, Priority>
  )

  @Serializable
  data class ComplexEnumObject(
    @Description("Primary status")
    val primaryStatus: Status,
    val secondaryStatuses: List<Status>,
    val statusMapping: Map<String, Status>,
    @Description("Optional priority")
    val priority: Priority? = null
  )

  // Test enums with @SerialName annotation
  @Serializable
  enum class HttpMethod {
    @SerialName("GET")
    GET,
    @SerialName("POST")
    POST,
    @SerialName("put")
    PUT,
    @SerialName("delete")
    DELETE
  }

  @Serializable
  enum class OrderStatus {
    @SerialName("pending_payment")
    PENDING_PAYMENT,
    @SerialName("processing")
    PROCESSING,
    @SerialName("shipped")
    SHIPPED,
    @SerialName("delivered")
    DELIVERED,
    @SerialName("cancelled")
    CANCELLED
  }

  // Mixed enum - some with SerialName, some without
  @Serializable
  enum class MixedEnum {
    @SerialName("custom_value_1")
    VALUE_ONE,
    VALUE_TWO,  // No SerialName, should use enum constant name
    @SerialName("custom_value_3")
    VALUE_THREE
  }

  @Serializable
  data class SerialNameEnumObject(
    val method: HttpMethod,
    val orderStatus: OrderStatus
  )

  @Serializable
  data class MixedSerialNameEnumObject(
    val mixedValue: MixedEnum,
    @Description("HTTP method for the request")
    val method: HttpMethod
  )

  @Serializable
  data class SerialNameEnumListObject(
    val methods: List<HttpMethod>,
    val statuses: List<OrderStatus>
  )

  @Serializable
  data class SerialNameEnumMapObject(
    val methodMap: Map<String, HttpMethod>,
    val statusMap: Map<String, OrderStatus>
  )

  @Serializable(with = CustomSerializer::class)
  class CustomSerializerObject

  object CustomSerializer : KSerializer<CustomSerializerObject> {
      override val descriptor: SerialDescriptor
          get() = PrimitiveSerialDescriptor(CustomSerializerObject::class.qualifiedName!!, PrimitiveKind.STRING)

      override fun serialize(
          encoder: Encoder,
          value: CustomSerializerObject
      ) {
          encoder.encodeString("CustomSerializerObject")
      }

      override fun deserialize(decoder: Decoder): CustomSerializerObject {
          return CustomSerializerObject()
      }
  }

  @Serializable
  data class ComplexJsonTypes(
    val jsonObject: JsonObject,
    val jsonElement: JsonElement,
    val jsonArray: JsonArray,
  )

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
  fun `generateJsonSchema fails gracefully for class without a serial descriptor`() {
    assertFailsWith<IllegalArgumentException> {
      NoSerializerClass::class.generateJsonSchema()
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

  @Test
  fun `generateJsonSchema handles enum types correctly`() {
    val schema = EnumObject::class.generateJsonSchema()
    val properties = schema["properties"] as JsonObject
    val required = schema["required"] as JsonArray

    // Verify status enum field
    val statusField = properties["status"] as JsonObject
    assertEquals(JsonPrimitive("string"), statusField["type"])
    assertTrue(statusField.containsKey("enum"))
    val statusEnum = statusField["enum"] as JsonArray
    assertEquals(4, statusEnum.size)
    val statusValues = statusEnum.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("ACTIVE", "INACTIVE", "PENDING", "ARCHIVED"), statusValues)

    // Verify priority enum field
    val priorityField = properties["priority"] as JsonObject
    assertEquals(JsonPrimitive("string"), priorityField["type"])
    assertTrue(priorityField.containsKey("enum"))
    val priorityEnum = priorityField["enum"] as JsonArray
    assertEquals(4, priorityEnum.size)
    val priorityValues = priorityEnum.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("LOW", "MEDIUM", "HIGH", "URGENT"), priorityValues)

    // Verify all fields are required
    val requiredFields = required.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("status", "priority"), requiredFields)
  }

  @Test
  fun `generateJsonSchema handles optional and nullable enum fields correctly`() {
    val schema = OptionalEnumObject::class.generateJsonSchema()
    val properties = schema["properties"] as JsonObject
    val required = schema["required"] as JsonArray

    // Verify required enum field
    val statusField = properties["status"] as JsonObject
    assertEquals(JsonPrimitive("string"), statusField["type"])
    assertTrue(statusField.containsKey("enum"))

    // Verify nullable enum field
    val priorityField = properties["priority"] as JsonObject
    assertEquals(JsonPrimitive("string"), priorityField["type"])
    assertTrue(priorityField.containsKey("enum"))

    // Verify optional enum field with default value
    val defaultStatusField = properties["defaultStatus"] as JsonObject
    assertEquals(JsonPrimitive("string"), defaultStatusField["type"])
    assertTrue(defaultStatusField.containsKey("enum"))

    // Verify required fields - only status should be required
    val requiredFields = required.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("status"), requiredFields)
  }

  @Test
  fun `generateJsonSchema handles enum fields with Description annotation correctly`() {
    val schema = DescribedEnumObject::class.generateJsonSchema()
    val properties = schema["properties"] as JsonObject

    // Verify status enum field with description
    val statusField = properties["status"] as JsonObject
    assertEquals(JsonPrimitive("string"), statusField["type"])
    assertEquals(JsonPrimitive("The current status of the object"), statusField["description"])
    assertTrue(statusField.containsKey("enum"))
    val statusEnum = statusField["enum"] as JsonArray
    val statusValues = statusEnum.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("ACTIVE", "INACTIVE", "PENDING", "ARCHIVED"), statusValues)

    // Verify priority enum field with description
    val priorityField = properties["priority"] as JsonObject
    assertEquals(JsonPrimitive("string"), priorityField["type"])
    assertEquals(JsonPrimitive("The priority level"), priorityField["description"])
    assertTrue(priorityField.containsKey("enum"))
    val priorityEnum = priorityField["enum"] as JsonArray
    val priorityValues = priorityEnum.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("LOW", "MEDIUM", "HIGH", "URGENT"), priorityValues)
  }

  @Test
  fun `generateJsonSchema handles lists of enums correctly`() {
    val schema = EnumListObject::class.generateJsonSchema()
    val properties = schema["properties"] as JsonObject

    // Verify list of status enums
    val statusesField = properties["statuses"] as JsonObject
    assertEquals(JsonPrimitive("array"), statusesField["type"])
    assertTrue(statusesField.containsKey("items"))
    val statusItems = statusesField["items"] as JsonObject
    assertEquals(JsonPrimitive("string"), statusItems["type"])
    assertTrue(statusItems.containsKey("enum"))
    val statusEnum = statusItems["enum"] as JsonArray
    val statusValues = statusEnum.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("ACTIVE", "INACTIVE", "PENDING", "ARCHIVED"), statusValues)

    // Verify list of priority enums
    val prioritiesField = properties["priorities"] as JsonObject
    assertEquals(JsonPrimitive("array"), prioritiesField["type"])
    assertTrue(prioritiesField.containsKey("items"))
    val priorityItems = prioritiesField["items"] as JsonObject
    assertEquals(JsonPrimitive("string"), priorityItems["type"])
    assertTrue(priorityItems.containsKey("enum"))
    val priorityEnum = priorityItems["enum"] as JsonArray
    val priorityValues = priorityEnum.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("LOW", "MEDIUM", "HIGH", "URGENT"), priorityValues)
  }

  @Test
  fun `generateJsonSchema handles maps with enum values correctly`() {
    val schema = EnumMapObject::class.generateJsonSchema()
    val properties = schema["properties"] as JsonObject

    // Verify map with status enum values
    val statusMapField = properties["statusMap"] as JsonObject
    assertEquals(JsonPrimitive("object"), statusMapField["type"])
    assertTrue(statusMapField.containsKey("additionalProperties"))
    val statusAdditionalProps = statusMapField["additionalProperties"] as JsonObject
    assertEquals(JsonPrimitive("string"), statusAdditionalProps["type"])
    assertTrue(statusAdditionalProps.containsKey("enum"))
    val statusEnum = statusAdditionalProps["enum"] as JsonArray
    val statusValues = statusEnum.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("ACTIVE", "INACTIVE", "PENDING", "ARCHIVED"), statusValues)

    // Verify map with priority enum values
    val priorityMapField = properties["priorityMap"] as JsonObject
    assertEquals(JsonPrimitive("object"), priorityMapField["type"])
    assertTrue(priorityMapField.containsKey("additionalProperties"))
    val priorityAdditionalProps = priorityMapField["additionalProperties"] as JsonObject
    assertEquals(JsonPrimitive("string"), priorityAdditionalProps["type"])
    assertTrue(priorityAdditionalProps.containsKey("enum"))
    val priorityEnum = priorityAdditionalProps["enum"] as JsonArray
    val priorityValues = priorityEnum.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("LOW", "MEDIUM", "HIGH", "URGENT"), priorityValues)
  }

  @Test
  fun `generateJsonSchema handles complex objects with mixed enum usage correctly`() {
    val schema = ComplexEnumObject::class.generateJsonSchema()
    val properties = schema["properties"] as JsonObject
    val required = schema["required"] as JsonArray

    // Verify primary status enum with description
    val primaryStatusField = properties["primaryStatus"] as JsonObject
    assertEquals(JsonPrimitive("string"), primaryStatusField["type"])
    assertEquals(JsonPrimitive("Primary status"), primaryStatusField["description"])
    assertTrue(primaryStatusField.containsKey("enum"))
    val primaryStatusEnum = primaryStatusField["enum"] as JsonArray
    val primaryStatusValues = primaryStatusEnum.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("ACTIVE", "INACTIVE", "PENDING", "ARCHIVED"), primaryStatusValues)

    // Verify list of status enums
    val secondaryStatusesField = properties["secondaryStatuses"] as JsonObject
    assertEquals(JsonPrimitive("array"), secondaryStatusesField["type"])
    assertTrue(secondaryStatusesField.containsKey("items"))
    val statusItems = secondaryStatusesField["items"] as JsonObject
    assertEquals(JsonPrimitive("string"), statusItems["type"])
    assertTrue(statusItems.containsKey("enum"))

    // Verify map with status enum values
    val statusMappingField = properties["statusMapping"] as JsonObject
    assertEquals(JsonPrimitive("object"), statusMappingField["type"])
    assertTrue(statusMappingField.containsKey("additionalProperties"))
    val statusAdditionalProps = statusMappingField["additionalProperties"] as JsonObject
    assertEquals(JsonPrimitive("string"), statusAdditionalProps["type"])
    assertTrue(statusAdditionalProps.containsKey("enum"))

    // Verify optional priority enum with description
    val priorityField = properties["priority"] as JsonObject
    assertEquals(JsonPrimitive("string"), priorityField["type"])
    assertEquals(JsonPrimitive("Optional priority"), priorityField["description"])
    assertTrue(priorityField.containsKey("enum"))
    val priorityEnum = priorityField["enum"] as JsonArray
    val priorityValues = priorityEnum.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("LOW", "MEDIUM", "HIGH", "URGENT"), priorityValues)

    // Verify required fields - priority should not be required (has default null)
    val requiredFields = required.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("primaryStatus", "secondaryStatuses", "statusMapping"), requiredFields)
  }

  @Test
  fun `generateJsonSchema handles enum with SerialName annotation correctly`() {
    val schema = SerialNameEnumObject::class.generateJsonSchema()
    val properties = schema["properties"] as JsonObject
    val required = schema["required"] as JsonArray

    // Verify HTTP method enum field uses @SerialName values
    val methodField = properties["method"] as JsonObject
    assertEquals(JsonPrimitive("string"), methodField["type"])
    assertTrue(methodField.containsKey("enum"))
    val methodEnum = methodField["enum"] as JsonArray
    assertEquals(4, methodEnum.size)
    val methodValues = methodEnum.map { (it as JsonPrimitive).content }.toSet()
    // Should use SerialName values, not enum constant names
    assertEquals(setOf("GET", "POST", "put", "delete"), methodValues)

    // Verify order status enum field uses @SerialName values
    val orderStatusField = properties["orderStatus"] as JsonObject
    assertEquals(JsonPrimitive("string"), orderStatusField["type"])
    assertTrue(orderStatusField.containsKey("enum"))
    val orderStatusEnum = orderStatusField["enum"] as JsonArray
    assertEquals(5, orderStatusEnum.size)
    val orderStatusValues = orderStatusEnum.map { (it as JsonPrimitive).content }.toSet()
    // Should use SerialName values (snake_case), not enum constant names
    assertEquals(setOf("pending_payment", "processing", "shipped", "delivered", "cancelled"), orderStatusValues)

    // Verify all fields are required
    val requiredFields = required.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("method", "orderStatus"), requiredFields)
  }

  @Test
  fun `generateJsonSchema handles mixed SerialName enum correctly`() {
    val schema = MixedSerialNameEnumObject::class.generateJsonSchema()
    val properties = schema["properties"] as JsonObject

    // Verify mixed enum field - some with SerialName, some without
    val mixedValueField = properties["mixedValue"] as JsonObject
    assertEquals(JsonPrimitive("string"), mixedValueField["type"])
    assertTrue(mixedValueField.containsKey("enum"))
    val mixedEnum = mixedValueField["enum"] as JsonArray
    assertEquals(3, mixedEnum.size)
    val mixedValues = mixedEnum.map { (it as JsonPrimitive).content }.toSet()
    // Should use SerialName where present, enum constant name where not
    assertEquals(setOf("custom_value_1", "VALUE_TWO", "custom_value_3"), mixedValues)

    // Verify method field with description and SerialName
    val methodField = properties["method"] as JsonObject
    assertEquals(JsonPrimitive("string"), methodField["type"])
    assertEquals(JsonPrimitive("HTTP method for the request"), methodField["description"])
    assertTrue(methodField.containsKey("enum"))
    val methodEnum = methodField["enum"] as JsonArray
    val methodValues = methodEnum.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("GET", "POST", "put", "delete"), methodValues)
  }

  @Test
  fun `generateJsonSchema handles lists of SerialName enums correctly`() {
    val schema = SerialNameEnumListObject::class.generateJsonSchema()
    val properties = schema["properties"] as JsonObject

    // Verify list of HTTP method enums with SerialName
    val methodsField = properties["methods"] as JsonObject
    assertEquals(JsonPrimitive("array"), methodsField["type"])
    assertTrue(methodsField.containsKey("items"))
    val methodItems = methodsField["items"] as JsonObject
    assertEquals(JsonPrimitive("string"), methodItems["type"])
    assertTrue(methodItems.containsKey("enum"))
    val methodEnum = methodItems["enum"] as JsonArray
    val methodValues = methodEnum.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("GET", "POST", "put", "delete"), methodValues)

    // Verify list of order status enums with SerialName
    val statusesField = properties["statuses"] as JsonObject
    assertEquals(JsonPrimitive("array"), statusesField["type"])
    assertTrue(statusesField.containsKey("items"))
    val statusItems = statusesField["items"] as JsonObject
    assertEquals(JsonPrimitive("string"), statusItems["type"])
    assertTrue(statusItems.containsKey("enum"))
    val statusEnum = statusItems["enum"] as JsonArray
    val statusValues = statusEnum.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("pending_payment", "processing", "shipped", "delivered", "cancelled"), statusValues)
  }

  @Test
  fun `generateJsonSchema handles custom serializer correctly`() {
    val schema = CustomSerializerObject::class.generateJsonSchema()

    // Verify that this custom serializer object is treated as a string based on the descriptor, and not as an object
    assertEquals(JsonPrimitive("string"), schema["type"])
  }

  @Test
  fun `generateJsonSchema handles maps with SerialName enum values correctly`() {
    val schema = SerialNameEnumMapObject::class.generateJsonSchema()
    val properties = schema["properties"] as JsonObject

    // Verify map with HTTP method enum values using SerialName
    val methodMapField = properties["methodMap"] as JsonObject
    assertEquals(JsonPrimitive("object"), methodMapField["type"])
    assertTrue(methodMapField.containsKey("additionalProperties"))
    val methodAdditionalProps = methodMapField["additionalProperties"] as JsonObject
    assertEquals(JsonPrimitive("string"), methodAdditionalProps["type"])
    assertTrue(methodAdditionalProps.containsKey("enum"))
    val methodEnum = methodAdditionalProps["enum"] as JsonArray
    val methodValues = methodEnum.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("GET", "POST", "put", "delete"), methodValues)

    // Verify map with order status enum values using SerialName
    val statusMapField = properties["statusMap"] as JsonObject
    assertEquals(JsonPrimitive("object"), statusMapField["type"])
    assertTrue(statusMapField.containsKey("additionalProperties"))
    val statusAdditionalProps = statusMapField["additionalProperties"] as JsonObject
    assertEquals(JsonPrimitive("string"), statusAdditionalProps["type"])
    assertTrue(statusAdditionalProps.containsKey("enum"))
    val statusEnum = statusAdditionalProps["enum"] as JsonArray
    val statusValues = statusEnum.map { (it as JsonPrimitive).content }.toSet()
    assertEquals(setOf("pending_payment", "processing", "shipped", "delivered", "cancelled"), statusValues)
  }

  @Test
  fun `complex JSON types are handled correctly`() {
    val schema = ComplexJsonTypes::class.generateJsonSchema()
    val properties = schema["properties"] as JsonObject

    // Verify jsonObject field
    val jsonObjectField = properties["jsonObject"] as JsonObject
    assertEquals(JsonPrimitive("object"), jsonObjectField["type"])
    assertTrue(jsonObjectField.containsKey("properties") || jsonObjectField.isNotEmpty())

    // Verify jsonElement field
    val jsonElementField = properties["jsonElement"] as JsonObject
    assertEquals(JsonPrimitive("object"), jsonElementField["type"])

    // Verify jsonArray field
    val jsonArrayField = properties["jsonArray"] as JsonObject
    assertEquals(JsonPrimitive("array"), jsonArrayField["type"])
    assertTrue(jsonArrayField.containsKey("items"))
  }
}

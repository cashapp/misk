package misk.web.metadata

import com.squareup.protos.test.kt.parsing.Robot as KotlinProtoRobot
import com.squareup.protos.test.kt.parsing.Shipment as KotlinProtoShipment
import com.squareup.protos.test.kt.parsing.Warehouse as KotlinProtoWarehouse
import com.squareup.protos.test.parsing.Robot
import com.squareup.protos.test.parsing.Shipment
import com.squareup.protos.test.parsing.Warehouse
import kotlin.reflect.full.createType
import misk.web.MiskWebFormBuilder
import misk.web.MiskWebFormBuilder.Field
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

enum class TestColor {
  RED,
  GREEN,
  BLUE,
}

data class SimpleDataClass(val name: String, val age: Int, val score: Double, val active: Boolean, val count: Long)

data class NestedInner(val value: String, val count: Int)

data class NestedDataClass(val label: String, val inner: NestedInner)

data class DataClassWithCollections(
  val tags: List<String>,
  val counts: List<Int>,
  val metadata: Map<String, String>,
  val nested: List<NestedInner>,
)

data class DataClassWithEnum(val name: String, val color: TestColor)

data class DataClassWithNullables(
  val required: String,
  val optional: String?,
  val maybeCount: Int?,
  val maybeInner: NestedInner?,
)

class NotADataClass(val value: String)

internal class MiskWebFormBuilderTest {
  private val miskWebFormBuilder = MiskWebFormBuilder { "https://$it" }

  @Test
  fun `handles null`() {
    assertThat(miskWebFormBuilder.calculateTypes(null)).isEmpty()
  }

  @Test
  fun `handles non-wire messages`() {
    assertThat(miskWebFormBuilder.calculateTypes(String::class.createType())).isEmpty()
  }

  @Test
  fun `handles java wire messages`() {
    val types = miskWebFormBuilder.calculateTypes(Shipment::class.createType())

    // Check Message Types
    assertThat(types).hasSize(3)
    assertThat(types).containsKey(Shipment::class.qualifiedName)
    assertThat(types).containsKey(Warehouse::class.qualifiedName)
    assertThat(types).containsKey(Robot::class.qualifiedName)

    val shipmentType = types[Shipment::class.qualifiedName]!!
    val warehouseType = types[Warehouse::class.qualifiedName]!!
    val robotType = types[Robot::class.qualifiedName]!!

    assertThat(shipmentType.documentationUrl).isEqualTo("https://test.Shipment")
    assertThat(warehouseType.documentationUrl).isEqualTo("https://test.Warehouse")
    assertThat(robotType.documentationUrl).isEqualTo("https://test.Robot")

    // Check primitive types
    assertThat(shipmentType.fields)
      .contains(
        Field("shipment_id", "Long", false, listOf("@com.squareup.protos.test.SemanticDataTypeOption({SHIPMENT_ID})"))
      )
    assertThat(warehouseType.fields)
      .contains(
        Field(
          "warehouse_token",
          "String",
          false,
          listOf("@com.squareup.protos.test.SemanticDataTypeOption({WAREHOUSE_TOKEN})"),
        )
      )

    // Check repeated types
    assertThat(shipmentType.fields)
      .contains(
        Field(
          "notes",
          "String",
          true,
          listOf("@com.squareup.protos.test.SemanticDataTypeOption({NOTE_TYPE_1, NOTE_TYPE_2})"),
        )
      )
    assertThat(warehouseType.fields)
      .contains(
        Field(name = "alternates", type = Warehouse::class.qualifiedName!!, repeated = true, annotations = emptyList())
      )

    // Check map value types are included
    assertThat(warehouseType.fields)
      .contains(
        Field(
          "robots",
          "com.squareup.protos.test.parsing.Robot",
          true,
          listOf("@com.squareup.protos.test.SemanticDataTypeOption({ROBOTS})"),
        )
      )
    // Check that we recurse into value types in Maps
    assertThat(robotType.fields)
      .contains(
        Field("robot_token", "String", false, listOf("@com.squareup.protos.test.SemanticDataTypeOption({ROBOT_TOKEN})"))
      )

    // Check enum types
    assertThat(shipmentType.fields)
      .contains(
        Field(
          name = "status",
          type =
            "Enum<com.squareup.protos.test.parsing.Shipment.State,VALIDATING,PICKING_UP," + "DELIVERING,CONSUMING>",
          repeated = false,
          listOf("@com.squareup.protos.test.SemanticDataTypeOption({STATUS})"),
        )
      )

    // Check oneof types
    assertThat(shipmentType.fields)
      .contains(
        Field(
          name = "account_token",
          type = "String",
          repeated = false,
          annotations = listOf("@com.squareup.protos.test.SemanticDataTypeOption({ACCOUNT_TOKEN})"),
        )
      )
  }

  @Test
  fun `handles kotlin wire messages`() {
    val types = miskWebFormBuilder.calculateTypes(KotlinProtoShipment::class.createType())

    // Check Message Types
    assertThat(types).hasSize(3)
    assertThat(types).containsKey(KotlinProtoShipment::class.qualifiedName)
    assertThat(types).containsKey(KotlinProtoWarehouse::class.qualifiedName)
    assertThat(types).containsKey(KotlinProtoRobot::class.qualifiedName)

    val shipmentType = types[KotlinProtoShipment::class.qualifiedName]!!
    val warehouseType = types[KotlinProtoWarehouse::class.qualifiedName]!!
    val robotType = types[KotlinProtoRobot::class.qualifiedName]!!

    assertThat(shipmentType.documentationUrl).isEqualTo("https://test.kt.Shipment")
    assertThat(warehouseType.documentationUrl).isEqualTo("https://test.kt.Warehouse")
    assertThat(robotType.documentationUrl).isEqualTo("https://test.kt.Robot")

    // Check primitive types
    assertThat(shipmentType.fields)
      .contains(
        Field(
          "shipment_id",
          "Long",
          false,
          listOf("@com.squareup.protos.test.kt.SemanticDataTypeOption({SHIPMENT_ID})"),
        )
      )
    assertThat(warehouseType.fields)
      .contains(
        Field(
          "warehouse_token",
          "String",
          false,
          listOf("@com.squareup.protos.test.kt.SemanticDataTypeOption({WAREHOUSE_TOKEN})"),
        )
      )

    // Check repeated types
    assertThat(shipmentType.fields)
      .contains(
        Field(
          "notes",
          "String",
          true,
          listOf("@com.squareup.protos.test.kt.SemanticDataTypeOption({NOTE_TYPE_1, NOTE_TYPE_2})"),
        )
      )
    assertThat(warehouseType.fields)
      .contains(
        Field(
          name = "alternates",
          type = KotlinProtoWarehouse::class.qualifiedName!!,
          repeated = true,
          annotations = emptyList(),
        )
      )

    // Check map value types are included
    assertThat(warehouseType.fields)
      .contains(
        Field(
          "robots",
          "com.squareup.protos.test.kt.parsing.Robot",
          true,
          listOf("@com.squareup.protos.test.kt.SemanticDataTypeOption({ROBOTS})"),
        )
      )
    // Check that we recurse into value types in Maps
    assertThat(robotType.fields)
      .contains(
        Field(
          "robot_token",
          "String",
          false,
          listOf("@com.squareup.protos.test.kt.SemanticDataTypeOption({ROBOT_TOKEN})"),
        )
      )

    // Check enum types
    assertThat(shipmentType.fields)
      .contains(
        Field(
          name = "status",
          type =
            "Enum<com.squareup.protos.test.kt.parsing.Shipment.State,VALIDATING,PICKING_UP," + "DELIVERING,CONSUMING>",
          repeated = false,
          annotations = listOf("@com.squareup.protos.test.kt.SemanticDataTypeOption({STATUS})"),
        )
      )

    // Check oneof types
    assertThat(shipmentType.fields)
      .contains(
        Field(
          name = "account_token",
          type = "String",
          repeated = false,
          annotations = listOf("@com.squareup.protos.test.kt.SemanticDataTypeOption({ACCOUNT_TOKEN})"),
        )
      )
  }

  @Test
  fun `handles simple data class with primitive fields`() {
    val types = miskWebFormBuilder.calculateTypes(SimpleDataClass::class.createType())

    assertThat(types).hasSize(1)
    assertThat(types).containsKey(SimpleDataClass::class.qualifiedName)

    val fields = types[SimpleDataClass::class.qualifiedName]!!.fields
    assertThat(fields).contains(Field("name", "String", false))
    assertThat(fields).contains(Field("age", "Int", false))
    assertThat(fields).contains(Field("score", "Double", false))
    assertThat(fields).contains(Field("active", "Boolean", false))
    assertThat(fields).contains(Field("count", "Long", false))

    assertThat(types[SimpleDataClass::class.qualifiedName]!!.documentationUrl).isNull()
  }

  @Test
  fun `handles nested data classes`() {
    val types = miskWebFormBuilder.calculateTypes(NestedDataClass::class.createType())

    assertThat(types).hasSize(2)
    assertThat(types).containsKey(NestedDataClass::class.qualifiedName)
    assertThat(types).containsKey(NestedInner::class.qualifiedName)

    val outerFields = types[NestedDataClass::class.qualifiedName]!!.fields
    assertThat(outerFields).contains(Field("label", "String", false))
    assertThat(outerFields).contains(Field("inner", NestedInner::class.qualifiedName!!, false))

    val innerFields = types[NestedInner::class.qualifiedName]!!.fields
    assertThat(innerFields).contains(Field("value", "String", false))
    assertThat(innerFields).contains(Field("count", "Int", false))
  }

  @Test
  fun `handles data class with list and map fields`() {
    val types = miskWebFormBuilder.calculateTypes(DataClassWithCollections::class.createType())

    assertThat(types).hasSize(2)
    assertThat(types).containsKey(DataClassWithCollections::class.qualifiedName)
    assertThat(types).containsKey(NestedInner::class.qualifiedName)

    val fields = types[DataClassWithCollections::class.qualifiedName]!!.fields
    assertThat(fields).contains(Field("tags", "String", true))
    assertThat(fields).contains(Field("counts", "Int", true))
    assertThat(fields).contains(Field("metadata", "String", true))
    assertThat(fields).contains(Field("nested", NestedInner::class.qualifiedName!!, true))
  }

  @Test
  fun `handles data class with enum fields`() {
    val types = miskWebFormBuilder.calculateTypes(DataClassWithEnum::class.createType())

    assertThat(types).hasSize(1)

    val fields = types[DataClassWithEnum::class.qualifiedName]!!.fields
    assertThat(fields).contains(Field("name", "String", false))
    assertThat(fields).contains(Field("color", "Enum<misk.web.metadata.TestColor,RED,GREEN,BLUE>", false))
  }

  @Test
  fun `handles data class with nullable fields`() {
    val types = miskWebFormBuilder.calculateTypes(DataClassWithNullables::class.createType())

    assertThat(types).hasSize(2)
    assertThat(types).containsKey(DataClassWithNullables::class.qualifiedName)
    assertThat(types).containsKey(NestedInner::class.qualifiedName)

    val fields = types[DataClassWithNullables::class.qualifiedName]!!.fields
    assertThat(fields).contains(Field("required", "String", false))
    assertThat(fields).contains(Field("optional", "String", false))
    assertThat(fields).contains(Field("maybeCount", "Int", false))
    assertThat(fields).contains(Field("maybeInner", NestedInner::class.qualifiedName!!, false))
  }

  @Test
  fun `non-data-class non-wire types still return empty`() {
    assertThat(miskWebFormBuilder.calculateTypes(NotADataClass::class.createType())).isEmpty()
  }
}

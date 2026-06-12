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

internal class MiskWebFormBuilderTest {
  private val miskWebFormBuilder = MiskWebFormBuilder { "https://$it" }

  @Test
  fun `handles null`() {
    assertThat(miskWebFormBuilder.calculateTypes(null)).isEmpty()
  }

  @Test
  fun `handles plain data class with primitive fields`() {
    val types = miskWebFormBuilder.calculateTypes(SimpleRequest::class.createType())

    assertThat(types).containsKey(SimpleRequest::class.java.canonicalName)
    val type = types[SimpleRequest::class.java.canonicalName]!!
    assertThat(type.fields)
      .containsExactlyInAnyOrder(
        Field("id", "Long", false, emptyList()),
        Field("name", "String", false, emptyList()),
        Field("active", "Boolean", false, emptyList()),
      )
  }

  @Test
  fun `handles nullable fields on data class`() {
    val types = miskWebFormBuilder.calculateTypes(NullableRequest::class.createType())
    val type = types[NullableRequest::class.java.canonicalName]!!
    assertThat(type.fields)
      .containsExactlyInAnyOrder(
        Field("optionalName", "String", false, emptyList()),
        Field("optionalCount", "Int", false, emptyList()),
      )
  }

  @Test
  fun `handles list of primitive on data class`() {
    val types = miskWebFormBuilder.calculateTypes(StringListRequest::class.createType())
    val type = types[StringListRequest::class.java.canonicalName]!!
    assertThat(type.fields).containsExactly(Field("tokens", "String", repeated = true, emptyList()))
  }

  @Test
  fun `handles list of nested data class with recursion`() {
    val types = miskWebFormBuilder.calculateTypes(NestedListRequest::class.createType())

    assertThat(types).containsKey(NestedListRequest::class.java.canonicalName)
    assertThat(types).containsKey(NestedItem::class.java.canonicalName)

    val outer = types[NestedListRequest::class.java.canonicalName]!!
    assertThat(outer.fields)
      .containsExactly(Field("entries", NestedItem::class.java.canonicalName!!, repeated = true, emptyList()))

    val nested = types[NestedItem::class.java.canonicalName]!!
    assertThat(nested.fields)
      .containsExactlyInAnyOrder(
        Field("itemId", "Long", false, emptyList()),
        Field("note", "String", false, emptyList()),
      )
  }

  @Test
  fun `handles map on data class`() {
    val types = miskWebFormBuilder.calculateTypes(MapRequest::class.createType())
    val type = types[MapRequest::class.java.canonicalName]!!
    // Mirrors Wire-message behavior: map keys are skipped, values are emitted as a repeated field.
    assertThat(type.fields).containsExactly(Field("counts", "Int", repeated = true, emptyList()))
  }

  @Test
  fun `handles nested data classes`() {
    val types = miskWebFormBuilder.calculateTypes(OuterRequest::class.createType())

    assertThat(types).containsKey(OuterRequest::class.java.canonicalName)
    assertThat(types).containsKey(InnerRequest::class.java.canonicalName)

    val outer = types[OuterRequest::class.java.canonicalName]!!
    assertThat(outer.fields)
      .containsExactly(Field("inner", InnerRequest::class.java.canonicalName!!, repeated = false, emptyList()))

    val inner = types[InnerRequest::class.java.canonicalName]!!
    assertThat(inner.fields).containsExactly(Field("value", "String", false, emptyList()))
  }

  @Test
  fun `does not re-walk a visited type`() {
    // Self-referential structure: a data class with a list of itself. The visited-set guard
    // prevents infinite recursion and ensures the type only appears once in the output map.
    val types = miskWebFormBuilder.calculateTypes(RecursiveRequest::class.createType())

    assertThat(types).hasSize(1)
    assertThat(types).containsKey(RecursiveRequest::class.java.canonicalName)
    val type = types[RecursiveRequest::class.java.canonicalName]!!
    assertThat(type.fields)
      .contains(Field("children", RecursiveRequest::class.java.canonicalName!!, repeated = true, emptyList()))
  }

  @Test
  fun `handles enum fields on data class`() {
    val types = miskWebFormBuilder.calculateTypes(EnumRequest::class.createType())
    val type = types[EnumRequest::class.java.canonicalName]!!
    assertThat(type.fields)
      .containsExactly(
        Field(
          name = "color",
          type = "Enum<${SimpleColor::class.java.canonicalName},RED,GREEN,BLUE>",
          repeated = false,
          annotations = emptyList(),
        )
      )
  }

  @Test
  fun `handles data class containing wire message`() {
    val types = miskWebFormBuilder.calculateTypes(MixedRequest::class.createType())

    assertThat(types).containsKey(MixedRequest::class.java.canonicalName)
    // Nested Wire message gets walked via the WireField path, populating its proto fields too.
    assertThat(types).containsKey(KotlinProtoShipment::class.qualifiedName)
    assertThat(types).containsKey(KotlinProtoWarehouse::class.qualifiedName)

    val mixed = types[MixedRequest::class.java.canonicalName]!!
    assertThat(mixed.fields)
      .contains(
        Field("name", "String", false, emptyList()),
        Field("shipment", KotlinProtoShipment::class.qualifiedName!!, repeated = false, emptyList()),
      )
  }

  private data class SimpleRequest(val id: Long, val name: String, val active: Boolean)

  private data class NullableRequest(val optionalName: String?, val optionalCount: Int?)

  private data class StringListRequest(val tokens: List<String>)

  private data class NestedItem(val itemId: Long, val note: String?)

  private data class NestedListRequest(val entries: List<NestedItem>)

  private data class MapRequest(val counts: Map<String, Int>)

  private data class InnerRequest(val value: String)

  private data class OuterRequest(val inner: InnerRequest)

  private data class RecursiveRequest(val name: String, val children: List<RecursiveRequest>)

  private enum class SimpleColor {
    RED,
    GREEN,
    BLUE,
  }

  private data class EnumRequest(val color: SimpleColor)

  private data class MixedRequest(val name: String, val shipment: KotlinProtoShipment)

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
}

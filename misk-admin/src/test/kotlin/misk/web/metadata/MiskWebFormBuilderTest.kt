package misk.web.metadata

import com.squareup.protos.test.parsing.Robot
import com.squareup.protos.test.parsing.Shipment
import com.squareup.protos.test.parsing.Warehouse
import misk.web.MiskWebFormBuilder
import misk.web.MiskWebFormBuilder.Field
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.createType
import com.squareup.protos.test.kt.parsing.Shipment as KotlinProtoShipment
import com.squareup.protos.test.kt.parsing.Warehouse as KotlinProtoWarehouse
import com.squareup.protos.test.kt.parsing.Robot as KotlinProtoRobot

internal class MiskWebFormBuilderTest {
  private val miskWebFormBuilder = MiskWebFormBuilder { "https://$it" }

  @Test fun `handles null`() {
    assertThat(miskWebFormBuilder.calculateTypes(null)).isEmpty()
  }

  @Test fun `handles non-wire messages`() {
    assertThat(miskWebFormBuilder.calculateTypes(String::class.createType())).isEmpty()
  }

  @Test fun `handles java wire messages`() {
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
    assertThat(shipmentType.fields).contains(Field("shipment_id", "Long", false,
      listOf("@com.squareup.protos.test.SemanticDataTypeOption({SHIPMENT_ID})")))
    assertThat(warehouseType.fields).contains(Field("warehouse_token", "String", false,
      listOf("@com.squareup.protos.test.SemanticDataTypeOption({WAREHOUSE_TOKEN})")))

    // Check repeated types
    assertThat(shipmentType.fields).contains(Field("notes", "String", true,
      listOf("@com.squareup.protos.test.SemanticDataTypeOption({NOTE_TYPE_1, NOTE_TYPE_2})")))
    assertThat(warehouseType.fields).contains(
      Field(
        name = "alternates",
        type = Warehouse::class.qualifiedName!!,
        repeated = true,
        annotations = emptyList(),
      )
    )

    // Check map value types are included
    assertThat(warehouseType.fields).contains(Field("robots", "com.squareup.protos.test.parsing.Robot",
      true, listOf("@com.squareup.protos.test.SemanticDataTypeOption({ROBOTS})")))
    // Check that we recurse into value types in Maps
    assertThat(robotType.fields).contains(Field("robot_token", "String", false,
      listOf("@com.squareup.protos.test.SemanticDataTypeOption({ROBOT_TOKEN})")))

    // Check enum types
    assertThat(shipmentType.fields).contains(
      Field(
        name = "status",
        type = "Enum<com.squareup.protos.test.parsing.Shipment.State,VALIDATING,PICKING_UP," +
          "DELIVERING,CONSUMING>",
        repeated = false,
        listOf("@com.squareup.protos.test.SemanticDataTypeOption({STATUS})")
      )
    )

    // Check oneof types
    assertThat(shipmentType.fields).contains(
      Field(
        name = "account_token",
        type = "String",
        repeated = false,
        annotations = listOf("@com.squareup.protos.test.SemanticDataTypeOption({ACCOUNT_TOKEN})")
      )
    )
  }

  @Test fun `handles kotlin wire messages`() {
    val types =
      miskWebFormBuilder.calculateTypes(KotlinProtoShipment::class.createType())

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
    assertThat(shipmentType.fields).contains(Field("shipment_id", "Long",false,
      listOf("@com.squareup.protos.test.kt.SemanticDataTypeOption({SHIPMENT_ID})")))
    assertThat(warehouseType.fields).contains(Field("warehouse_token", "String", false,
      listOf("@com.squareup.protos.test.kt.SemanticDataTypeOption({WAREHOUSE_TOKEN})")))

    // Check repeated types
    assertThat(shipmentType.fields).contains(Field("notes", "String",true,
      listOf("@com.squareup.protos.test.kt.SemanticDataTypeOption({NOTE_TYPE_1, NOTE_TYPE_2})")))
    assertThat(warehouseType.fields).contains(
      Field(
        name = "alternates",
        type = KotlinProtoWarehouse::class.qualifiedName!!,
        repeated = true,
        annotations = emptyList(),
      )
    )

    // Check map value types are included
    assertThat(warehouseType.fields).contains(Field("robots", "com.squareup.protos.test.kt.parsing.Robot",
      true, listOf("@com.squareup.protos.test.kt.SemanticDataTypeOption({ROBOTS})")))
    // Check that we recurse into value types in Maps
    assertThat(robotType.fields).contains(Field("robot_token", "String", false,
      listOf("@com.squareup.protos.test.kt.SemanticDataTypeOption({ROBOT_TOKEN})")))

    // Check enum types
    assertThat(shipmentType.fields).contains(
      Field(
        name = "status",
        type = "Enum<com.squareup.protos.test.kt.parsing.Shipment.State,VALIDATING,PICKING_UP," +
          "DELIVERING,CONSUMING>",
        repeated = false,
        annotations = listOf("@com.squareup.protos.test.kt.SemanticDataTypeOption({STATUS})")

      )
    )

    // Check oneof types
    assertThat(shipmentType.fields).contains(
      Field(
        name = "account_token",
        type = "String",
        repeated = false,
        annotations = listOf("@com.squareup.protos.test.kt.SemanticDataTypeOption({ACCOUNT_TOKEN})")
      )
    )
  }
}

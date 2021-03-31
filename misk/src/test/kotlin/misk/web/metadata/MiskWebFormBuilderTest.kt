package misk.web.metadata

import com.squareup.protos.test.parsing.Shipment
import com.squareup.protos.test.parsing.Warehouse
import misk.web.MiskWebFormBuilder
import misk.web.MiskWebFormBuilder.Field
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.createType
import misk.web.metadata.protos.Shipment as KotlinProtoShipment
import misk.web.metadata.protos.Warehouse as KotlinProtoWarehouse

internal class MiskWebFormBuilderTest {
  private val miskWebFormBuilder = MiskWebFormBuilder()

  @Test fun `handles null`() {
    assertThat(miskWebFormBuilder.calculateTypes(null)).isEmpty()
  }

  @Test fun `handles non-wire messages`() {
    assertThat(miskWebFormBuilder.calculateTypes(String::class.createType())).isEmpty()
  }

  @Test fun `handles java wire messages`() {
    val types = miskWebFormBuilder.calculateTypes(Shipment::class.createType())

    // Check Message Types
    assertThat(types).hasSize(2)
    assertThat(types).containsKey(Shipment::class.qualifiedName)
    assertThat(types).containsKey(Warehouse::class.qualifiedName)

    val shipmentType = types[Shipment::class.qualifiedName]!!
    val warehouseType = types[Warehouse::class.qualifiedName]!!

    // Check primitive types
    assertThat(shipmentType.fields).contains(Field("shipment_id", "Long", false))
    assertThat(warehouseType.fields).contains(Field("warehouse_token", "String", false))

    // Check repeated types
    assertThat(shipmentType.fields).contains(Field("notes", "String", true))
    assertThat(warehouseType.fields).contains(
      Field(
        name = "alternates",
        type = Warehouse::class.qualifiedName!!,
        repeated = true
      )
    )

    // Check enum types
    assertThat(shipmentType.fields).contains(
      Field(
        name = "status",
        type = "Enum<com.squareup.protos.test.parsing.Shipment.State,VALIDATING,PICKING_UP," +
          "DELIVERING,CONSUMING>",
        repeated = false
      )
    )
  }

  @Test fun `handles kotlin wire messages`() {
    val types =
      miskWebFormBuilder.calculateTypes(KotlinProtoShipment::class.createType())

    // Check Message Types
    assertThat(types).hasSize(2)
    assertThat(types).containsKey(KotlinProtoShipment::class.qualifiedName)
    assertThat(types).containsKey(KotlinProtoWarehouse::class.qualifiedName)

    val shipmentType = types[KotlinProtoShipment::class.qualifiedName]!!
    val warehouseType = types[KotlinProtoWarehouse::class.qualifiedName]!!

    // Check primitive types
    assertThat(shipmentType.fields).contains(Field("shipment_id", "Long", false))
    assertThat(warehouseType.fields).contains(Field("warehouse_token", "String", false))

    // Check repeated types
    assertThat(shipmentType.fields).contains(Field("notes", "String", true))
    assertThat(warehouseType.fields).contains(
      Field(
        name = "alternates",
        type = KotlinProtoWarehouse::class.qualifiedName!!,
        repeated = true
      )
    )

    // Check enum types
    assertThat(shipmentType.fields).contains(
      Field(
        name = "status",
        type = "Enum<misk.web.metadata.protos.Shipment.State,VALIDATING,PICKING_UP," +
          "DELIVERING,CONSUMING>",
        repeated = false
      )
    )
  }
}

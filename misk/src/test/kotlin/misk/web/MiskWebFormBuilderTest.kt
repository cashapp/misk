package misk.web.metadata

import com.squareup.protos.test.parsing.Shipment
import com.squareup.protos.test.parsing.Warehouse
import misk.web.MiskWebFormBuilder
import misk.web.MiskWebFormBuilder.Field
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.createType

internal class MiskWebFormBuilderTest {
  val miskWebFormBuilder = MiskWebFormBuilder()

  @Test fun handlesNull() {
    assertThat(miskWebFormBuilder.calculateTypes(null)).isEmpty()
  }

  @Test fun handlesNonWireMessages() {
    assertThat(miskWebFormBuilder.calculateTypes(String::class.createType())).isEmpty()
  }

  @Test fun handlesWireMessages() {
    val types = miskWebFormBuilder.calculateTypes(Shipment::class.createType())

    // Check Message Types
    assertThat(types).hasSize(2)
    assertThat(types).containsKey(Shipment::class.qualifiedName)
    assertThat(types).containsKey(Warehouse::class.qualifiedName)

    val shipmentType = types.get(Shipment::class.qualifiedName)!!
    val warehouseType = types.get(Warehouse::class.qualifiedName)!!

    // Check primitive types
    assertThat(shipmentType.fields).contains(Field("shipment_id", "Long", false))
    assertThat(warehouseType.fields).contains(Field("warehouse_token", "String", false))

    // Check repeated types
    assertThat(shipmentType.fields).contains(Field("notes", "String", true))
    assertThat(warehouseType.fields).contains(
      Field(
        "alternates",
        Warehouse::class.qualifiedName!!,
        true
      )
    )

    // Check enum types
    assertThat(shipmentType.fields).contains(
      Field(
        "status",
        "Enum<com.squareup.protos.test.parsing.Shipment.State,VALIDATING,PICKING_UP," +
          "DELIVERING,CONSUMING>",
        false
      )
    )
  }
}

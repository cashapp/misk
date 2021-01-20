package misk.moshi.wire

import com.squareup.moshi.Moshi
import com.squareup.protos.test.parsing.Robot
import com.squareup.protos.test.parsing.Shipment
import com.squareup.protos.test.parsing.Warehouse
import misk.MiskTestingServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.isEqualToAsJson
import org.junit.jupiter.api.Test
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest
internal class WireMessageAdapterTest {
  @MiskTestModule
  val module = MiskTestingServiceModule()

  @Inject
  lateinit var moshi: Moshi

  @Test
  fun simpleTypes() {
    val warehouseAdapter = moshi.adapter(Warehouse::class.java)

    val warehouse = Warehouse.Builder()
        .warehouse_id(1014L)
        .warehouse_token("AAAA")
        .build()

    val json = warehouseAdapter.indent(" ").toJson(warehouse)
    assertThat(json).isEqualToAsJson("""
        |{
        | "warehouse_id": 1014,
        | "warehouse_token": "AAAA",
        | "alternates": [],
        | "dropoff_points": {},
        | "robots": {}
        |}
        |""".trimMargin())

    assertThat(warehouseAdapter.fromJson(json)).isEqualTo(warehouse)
  }

  @Test
  fun nestedTypes() {
    val shipmentAdapter = moshi.adapter(Shipment::class.java)

    val shipment = Shipment.Builder()
        .shipment_id(100075)
        .shipment_token("P_AAAAA")
        .status(Shipment.State.DELIVERING)
        .source(Warehouse.Builder()
            .warehouse_id(9999L)
            .warehouse_token("C_RANDY")
            .build())
        .destination(Warehouse.Builder()
            .warehouse_id(7777L)
            .warehouse_token("C_CATHY")
            .build())
        .deleted(true)
        .load_ratio(0.75)
        .notes(listOf("Note A", "Note B", "Note C"))
        .build()

    val json = shipmentAdapter.indent(" ").toJson(shipment)
    assertThat(json).isEqualToAsJson("""
        |{
        | "shipment_id": 100075,
        | "shipment_token": "P_AAAAA",
        | "source": {
        | "warehouse_id": 9999,
        | "warehouse_token": "C_RANDY",
        | "alternates": [],
        | "dropoff_points": {},
        | "robots": {}
        | },
        | "destination": {
        | "warehouse_id": 7777,
        | "warehouse_token": "C_CATHY",
        | "alternates": [],
        | "dropoff_points": {},
        | "robots": {}
        | },
        | "status": "DELIVERING",
        | "load_ratio": 0.75,
        | "deleted": true,
        | "notes": [
        | "Note A",
        | "Note B",
        | "Note C"
        | ]
        |}
        |""".trimMargin())
    assertThat(shipmentAdapter.fromJson(json)).isEqualTo(shipment)
  }

  @Test
  fun nestedRepeatingMessages() {
    val warehouseAdapter = moshi.adapter(Warehouse::class.java)
    val warehouse = Warehouse.Builder()
        .alternates(listOf(
            Warehouse.Builder()
                .warehouse_id(755L)
                .warehouse_token("W_AXAA")
                .build(),
            Warehouse.Builder()
                .warehouse_id(500L)
                .warehouse_token("W_THTHT")
                .build()))
        .build()
    val json = warehouseAdapter.indent(" ").toJson(warehouse)
    assertThat(json).isEqualToAsJson("""
        |{
        | "alternates": [
        |  {
        |   "warehouse_id": 755,
        |   "warehouse_token": "W_AXAA",
        |   "alternates": [],
        |   "dropoff_points": {},
        |   "robots": {}
        |  },
        |  {
        |   "warehouse_id": 500,
        |   "warehouse_token": "W_THTHT",
        |   "alternates": [],
        |   "dropoff_points": {},
        |   "robots": {}
        |  }
        | ],
        | "dropoff_points": {},
        | "robots": {}
        |}
        |""".trimMargin())
    assertThat(warehouseAdapter.fromJson(json)).isEqualTo(warehouse)
  }

  @Test
  fun missingListFieldsMapToEmptyLists() {
    val shipmentAdapter = moshi.adapter(Shipment::class.java)
    val parsed = shipmentAdapter.fromJson("""
        |{
        | "shipment_id": 100075,
        | "shipment_token": "P_AAAAA",
        | "load_ratio": 0.75,
        | "deleted": true
        |}""".trimMargin())!!

    val expected = Shipment.Builder()
        .shipment_id(100075)
        .shipment_token("P_AAAAA")
        .deleted(true)
        .load_ratio(0.75)
        .build()

    assertThat(parsed).isEqualTo(expected)
    assertThat(parsed.notes).isNotNull
  }

  @Test
  fun emptyListFieldsSerializeToEmptyLists() {
    val shipmentAdapter = moshi.adapter(Shipment::class.java)
    val shipment = Shipment.Builder()
        .notes(listOf())
        .build()
    val json = shipmentAdapter.indent(" ").toJson(shipment)
    assertThat(json).isEqualToAsJson("""
        |{
        | "notes": []
        |}
        |""".trimMargin())
  }

  @Test
  fun maps() {
    val warehouseAdapter = moshi.adapter(Warehouse::class.java)
    val warehouse = Warehouse.Builder()
        .warehouse_id(1976)
        .warehouse_token("W_ACDFD")
        .dropoff_points(mapOf(
            "station-1" to "left of north door A",
            "station-2" to "right of office",
            "station-3" to "left of center"
        ))
        .build()
    val json = warehouseAdapter.indent(" ").toJson(warehouse)
    assertThat(json).isEqualToAsJson("""
        |{
        | "warehouse_id": 1976,
        | "warehouse_token": "W_ACDFD",
        | "alternates": [],
        | "dropoff_points": {
        | "station-1": "left of north door A",
        | "station-2": "right of office",
        | "station-3": "left of center"
        | },
        | "robots": {}
        |}""".trimMargin())
    assertThat(warehouseAdapter.fromJson(json)).isEqualTo(warehouse)
  }

  @Test
  fun mapsOfMessages() {
    val warehouseAdapter = moshi.adapter(Warehouse::class.java)
    val warehouse = Warehouse.Builder()
        .warehouse_id(1976)
        .warehouse_token("W_ACDFD")
        .robots(mapOf(
            34 to Robot.Builder()
                .robot_id(34)
                .robot_token("R_93498")
                .build(),
            56 to Robot.Builder()
                .robot_id(56)
                .robot_token("R_DFGDD")
                .build()))
        .build()
    val json = warehouseAdapter.indent(" ").toJson(warehouse)
    assertThat(json).isEqualToAsJson("""
        |{
        | "warehouse_id": 1976,
        | "warehouse_token": "W_ACDFD",
        | "alternates": [],
        | "dropoff_points": {},
        | "robots": {
        |  "34": {
        |   "robot_id": 34,
        |   "robot_token": "R_93498"
        |  },
        |  "56": {
        |   "robot_id": 56,
        |   "robot_token": "R_DFGDD"
        |  }
        | }
        |}
        |""".trimMargin())
    assertThat(warehouseAdapter.fromJson(json)).isEqualTo(warehouse)
  }

  @Test
  fun detectsAndFailsOnMultipleOneOfs() {
    val shipmentAdapter = moshi.adapter(Shipment::class.java)
    assertThat(assertFailsWith<IllegalArgumentException> {
      shipmentAdapter.fromJson("""
        |{
        | "shipment_id": 100075,
        | "shipment_token": "P_AAAAA",
        | "load_ratio": 0.75,
        | "deleted": true,
        | "account_token": "AC_5765",
        | "card_token": "CC_34531"
        |}
        |""".trimMargin())
    }).hasMessage("at most one of account_token, card_token, transfer_id may be non-null")
  }

  @Test
  fun cyclicalTypes() {
    val warehouseAdapter = moshi.adapter(Warehouse::class.java)
    val warehouse = Warehouse.Builder()
        .warehouse_token("CDCDC")
        .warehouse_id(755L)
        .central_repo(Warehouse.Builder()
            .warehouse_id(1L)
            .warehouse_token("AAAAA")
            .build())
        .alternates(listOf(
            Warehouse.Builder()
                .warehouse_id(756)
                .warehouse_token("CDCDB")
                .build(),
            Warehouse.Builder()
                .warehouse_id(757)
                .warehouse_token("CDCDA")
                .build()))
        .build()

    val json = warehouseAdapter.indent(" ").toJson(warehouse)
    assertThat(json).isEqualToAsJson("""
        |{
        | "warehouse_id": 755,
        | "warehouse_token": "CDCDC",
        | "central_repo": {
        | "warehouse_id": 1,
        | "warehouse_token": "AAAAA",
        | "alternates": [],
        | "dropoff_points": {},
        | "robots": {}
        | },
        | "alternates": [
        | {
        |  "warehouse_id": 756,
        |  "warehouse_token": "CDCDB",
        |  "alternates": [],
        |  "dropoff_points": {},
        |  "robots": {}
        | },
        | {
        |  "warehouse_id": 757,
        |  "warehouse_token": "CDCDA",
        |  "alternates": [],
        |  "dropoff_points": {},
        |  "robots": {}
        | }
        | ],
        | "dropoff_points": {},
        | "robots": {}
        |}
        |""".trimMargin())
  }

  @Test fun explicitNull() {
    val warehouseAdapter = moshi.adapter(Warehouse::class.java)

    val expected = Warehouse.Builder()
        .alternates(listOf(
            Warehouse.Builder()
                .warehouse_id(1014L)
                .build()
        ))
        .build()

    val json = """
        |{
        |  "alternates": [
        |    {
        |      "warehouse_id": 1014,
        |      "warehouse_token": null
        |    }
        |  ]
        |}
        |""".trimMargin()

    assertThat(warehouseAdapter.fromJson(json)).isEqualTo(expected)
  }

  @Test
  fun byteStringsAreBase64() {
    val shipmentAdapter = moshi.adapter(Shipment::class.java)

    val shipment = Shipment.Builder()
        .shipment_id(100075)
        .shipment_token("P_AAAAA")
        .source_signature("98 34v59823wh;tiejs".encodeUtf8())
        .build()
    val jsonText = shipmentAdapter.indent(" ").toJson(shipment)
    assertThat(jsonText).isEqualToAsJson("""
        |{
        | "shipment_id": 100075,
        | "shipment_token": "P_AAAAA",
        | "source_signature": "OTggMzR2NTk4MjN3aDt0aWVqcw==",
        | "notes": []
        |}
        |""".trimMargin())
  }
}

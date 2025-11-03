package misk.vitess.testing.internal

import misk.vitess.testing.VitessTestDbSchemaLintException

internal class VschemaLinter(val vschemaAdapter: VschemaAdapter) {
  fun lint(vschemaJson: Map<String, Any>, keyspaceName: String) {
    if (vschemaJson["sharded"] as? Boolean == false) {
      throw VitessTestDbSchemaLintException("Omit `sharded = false` in the unsharded vschema of `$keyspaceName`")
    }

    val isSharded = vschemaJson["sharded"] as? Boolean == true

    if (isSharded) {
      lintShardedVschema(vschemaJson, keyspaceName)
    } else {
      lintUnshardedVschema(vschemaJson, keyspaceName)
    }
  }

  private fun lintShardedVschema(vschemaJson: Map<String, Any>, keyspaceName: String) {
    val vschemaFields = listOf("sharded", "vindexes", "tables")
    ensureFields(
      vschemaJson,
      vschemaFields,
      "The fields in the `sharded` vschema of `$keyspaceName` must be ordered as: $vschemaFields",
    )

    val vschemaVindexesMap = vschemaAdapter.toMap(vschemaJson["vindexes"])
    vschemaVindexesMap.let { vindexes ->
      ensureFields(
        vindexes,
        vindexes.keys.sorted(),
        "The vindexes of the vschema for `$keyspaceName` must be ordered alphabetically",
      )
      vindexes.forEach { (vindexName, vindex) ->
        val vindexMap = vschemaAdapter.toMap(vindex)
        if (vindexMap["type"] == null) {
          throw VitessTestDbSchemaLintException(
            "The vindex `$vindexName` in the vschema of `$keyspaceName` must contain a `type` field"
          )
        }
        val vindexType = (vindexMap["type"] as? String)

        if (vindexType?.contains("lookup") == true) {
          val orderedLookupFields = listOf("type", "params", "owner")
          ensureFields(
            vindexMap,
            orderedLookupFields,
            "The fields in the `lookup` vindex `$vindexName` in the vschema of `$keyspaceName` must be ordered as: $orderedLookupFields",
          )

          val vindexParamsMap = vschemaAdapter.toMap(vindexMap["params"])
          vindexParamsMap.let { params ->
            val orderedParamsFields = mutableListOf("autocommit", "from", "ignore_nulls", "table", "to")
            if (params.containsKey("write_only")) {
              orderedParamsFields.add("write_only")
            }
            ensureFields(
              params,
              orderedParamsFields,
              "The `params` fields in the `lookup` vindex `$vindexName` in the vschema of `$keyspaceName` must be ordered as: $orderedParamsFields",
            )

            // Check if "to" field exists and equals "keyspace_id"
            validateLookupVindex(
              vindexType,
              params,
              keyspaceName,
              vindexName
            )
          }
        }
      }
    }

    val vschemaTablesMap = vschemaAdapter.toMap(vschemaJson["tables"])
    vschemaTablesMap.let { tables ->
      ensureFields(
        tables,
        tables.keys.sorted(),
        "The table entries in the vschema of `$keyspaceName` must be ordered alphabetically",
      )
      tables.forEach { (tableName, table) ->
        val tableMap = vschemaAdapter.toMap(table)
        if (tableMap["column_vindexes"] == null) {
          throw VitessTestDbSchemaLintException(
            "The table `$tableName` in the vschema of `$keyspaceName` must contain a `column_vindexes` field"
          )
        }

        if (tableMap.keys.size > 2) {
          throw VitessTestDbSchemaLintException(
            "The table `$tableName` in the vschema of `$keyspaceName` can only have the fields `column_vindexes` and `auto_increment`"
          )
        }

        val tableFields = mutableListOf("column_vindexes")
        val includeAutoIncrement = (tableMap.keys.size == 2)
        if (includeAutoIncrement) {
          tableFields.add("auto_increment")
        }

        ensureFields(
          tableMap,
          tableFields,
          "The fields for table `$tableName` in the vschema of `$keyspaceName` must be ordered as: $tableFields",
        )

        val columnVindexFields = listOf("column", "name")
        val columnVindexList = vschemaAdapter.toListMap(tableMap["column_vindexes"])
        columnVindexList.forEachIndexed { index, columnVindex ->
          ensureFields(
            columnVindex,
            columnVindexFields,
            "The fields in the `column_vindex` in table `$tableName` at index `$index` in the vschema of `$keyspaceName` must be ordered as `$columnVindexFields`",
          )

          val vindexName = columnVindex["name"]
          if (!vschemaVindexesMap.containsKey(vindexName)) {
            throw VitessTestDbSchemaLintException(
              "The `column_vindex` name `$vindexName` for table `$tableName` at index `$index` in the vschema of `$keyspaceName` is not defined in `vindexes`"
            )
          }
        }

        if (includeAutoIncrement) {
          val autoIncrementFields = listOf("column", "sequence")
          val autoIncrementMap = vschemaAdapter.toMap(tableMap["auto_increment"])
          ensureFields(
            autoIncrementMap,
            autoIncrementFields,
            "The `auto_increment` fields in table `$tableName` in the vschema of `$keyspaceName` must be ordered as `$autoIncrementFields`",
          )
        }
      }
    }
  }

  private fun lintUnshardedVschema(vschemaJson: Map<String, Any>, keyspaceName: String) {
    val vschemaFields = listOf("tables")
    ensureFields(
      vschemaJson,
      vschemaFields,
      "The `unsharded` vschema for `$keyspaceName` must only have the field `tables`",
    )

    vschemaAdapter.toMap(vschemaJson["tables"]).let { tables ->
      ensureFields(
        tables,
        tables.keys.sorted(),
        "The tables entries in the vschema of `$keyspaceName` must be ordered alphabetically",
      )
      tables.forEach { (tableName, table) ->
        val tableMap = vschemaAdapter.toMap(table)
        if (tableMap.isNotEmpty() && (tableMap["type"] as? String !in listOf("sequence", "reference"))) {
          throw VitessTestDbSchemaLintException(
            "The table `$tableName` in the `unsharded` vschema of `$keyspaceName` must either be an empty object or have a type of `sequence` or `reference`"
          )
        }
      }
    }
  }

  private fun validateLookupVindex(
    vindexType: String,
    params: Map<String, Any>,
    keyspaceName: String,
    vindexName: String,
  ) {
    // if vindexType is "lookup_hash" or "lookup_unicodeloosemd5_hash", check we want to ensure that the
    // "to" field is not "keyspace_id"
    var vIndexIsHashBased = vindexType.endsWith("_hash")
    var toIsKeyspaceId = params["to"] == "keyspace_id"
    var toParam = params["to"]

    when {
      // hash based vindex should not have a `to = "keyspace_id"`
      vIndexIsHashBased && toIsKeyspaceId ->
        throw VitessTestDbSchemaLintException(
          "The lookup vindex `$vindexName` in keyspace `$keyspaceName` " +
            "has an invalid `to` parameter of `keyspace_id`,  as `keyspace_id` is reserved for new lookup types."
        )

      // if it is not hashbased, we require `keyspace_id` to be set in the `to` field.
      !vIndexIsHashBased && !toIsKeyspaceId ->
        throw VitessTestDbSchemaLintException(
          "The lookup vindex `$vindexName` in keyspace `$keyspaceName` has an invalid `to` parameter of " +
            "`$toParam`, expected `keyspace_id` for modern lookup types."
        )
      else -> {}
    }
  }

  private fun ensureFields(jsonObject: Map<String, Any>, orderedFields: List<String>, errorMessage: String) {
    val jsonFields = jsonObject.keys.toList()
    if (!orderedFields.all { jsonFields.contains(it) }) {
      throw VitessTestDbSchemaLintException(errorMessage)
    }
    if (jsonFields != orderedFields) {
      throw VitessTestDbSchemaLintException(errorMessage)
    }
  }
}

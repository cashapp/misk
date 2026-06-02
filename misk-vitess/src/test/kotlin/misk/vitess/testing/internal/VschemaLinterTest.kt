package misk.vitess.testing.internal

import misk.vitess.testing.VitessTestDbSchemaLintException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class VschemaLinterTest {

  private val vschemaAdapter = VschemaAdapter()
  private val linter = VschemaLinter(vschemaAdapter)

  @Test
  fun `test unsharded vschema with valid sequence type`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
            {
                "tables": {
                    "customers_seq": {
                      "type": "sequence"
                     }
                }
            }
        """
      )!!

    assertDoesNotThrow { linter.lint(vschemaJson, "unsharded") }
  }

  @Test
  fun `test unsharded vschema with valid reference type`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
            {
                "tables": {
                    "my_reference_table": {
                      "type": "reference"
                     }
                }
            }
        """
      )!!

    assertDoesNotThrow { linter.lint(vschemaJson, "unsharded") }
  }

  @Test
  fun `test unsharded vschema with invalid type`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
            {
                "tables": {
                    "customers": {
                      "type": "invalid"
                     }
                }
            }
        """
      )!!

    val exception = assertThrows<VitessTestDbSchemaLintException> { linter.lint(vschemaJson, "unsharded") }
    assertEquals(
      "The table `customers` in the `unsharded` vschema of `unsharded` must either be an empty object or have a type of `sequence` or `reference`",
      exception.message,
    )
  }

  @Test
  fun `test sharded vschema with valid vschema fields`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
            {
                "sharded": true,
                "vindexes": {},
                "tables": {}
            }
        """
      )!!

    assertDoesNotThrow { linter.lint(vschemaJson, "unsharded") }
  }

  @Test
  fun `test sharded vschema with invalid vschema fields order`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
            {
                "tables": {},
                "sharded": true,
                "vindexes": {}
            }
        """
      )!!

    val exception = assertThrows<VitessTestDbSchemaLintException> { linter.lint(vschemaJson, "sharded") }
    assertEquals(
      "The fields in the `sharded` vschema of `sharded` must be ordered as: [sharded, vindexes, tables]",
      exception.message,
    )
  }

  @Test
  fun `test sharded vschema with missing vindexes`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
            {
                "sharded": true,
                "tables": {}
            }
        """
      )!!

    val exception = assertThrows<VitessTestDbSchemaLintException> { linter.lint(vschemaJson, "sharded") }
    assertEquals(
      "The fields in the `sharded` vschema of `sharded` must be ordered as: [sharded, vindexes, tables]",
      exception.message,
    )
  }

  @Test
  fun `test sharded vschema with vindex missing type`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
          {
              "sharded": true,
              "vindexes": {
                  "hash": {}
              },
              "tables": {}
          }
      """
      )!!

    val exception = assertThrows<VitessTestDbSchemaLintException> { linter.lint(vschemaJson, "sharded") }
    assertEquals("The vindex `hash` in the vschema of `sharded` must contain a `type` field", exception.message)
  }

  @Test
  fun `test sharded vschema with valid lookup vindex with write_only`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
            {
                "sharded": true,
                "vindexes": {
                    "customers_email_lookup": {
                        "type": "lookup",
                        "params": {
                            "autocommit": "true",
                            "from": "email",
                            "ignore_nulls": "true",
                            "table": "customers_email_lookup",
                            "to": "keyspace_id",
                            "write_only": "true"
                        },
                        "owner": "customers"
                    }
                },
                "tables": {}
            }
        """
      )!!

    assertDoesNotThrow { linter.lint(vschemaJson, "sharded") }
  }

  @Test
  fun `test sharded vschema with valid lookup vindex with no_verify`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
            {
                "sharded": true,
                "vindexes": {
                    "customers_email_lookup": {
                        "type": "lookup",
                        "params": {
                            "autocommit": "true",
                            "from": "email",
                            "ignore_nulls": "true",
                            "no_verify": "true",
                            "table": "customers_email_lookup",
                            "to": "keyspace_id"
                        },
                        "owner": "customers"
                    }
                },
                "tables": {}
            }
        """
      )!!

    assertDoesNotThrow { linter.lint(vschemaJson, "sharded") }
  }

  @Test
  fun `test sharded vschema with invalid lookup vindex with`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
            {
                "sharded": true,
                "vindexes": {
                    "customers_email_lookup": {
                        "type": "lookup",
                        "params": {
                            "autocommit": "true",
                            "from": "email",
                            "ignore_nulls": "true",
                            "table": "customers_email_lookup",
                            "to": "customer_id",
                            "write_only": "true"
                        },
                        "owner": "customers"
                    }
                },
                "tables": {}
            }
        """
      )

    val exception = assertThrows<VitessTestDbSchemaLintException> { linter.lint(vschemaJson, "sharded") }
    assertEquals(
      "The lookup vindex `customers_email_lookup` in keyspace `sharded` has an invalid " +
        "`to` parameter of `customer_id`, expected `keyspace_id` for modern lookup types.",
      exception.message,
    )
  }

  @Test
  fun `test sharded vschema with invalid lookup_hash vindex with`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
            {
                "sharded": true,
                "vindexes": {
                    "customers_email_lookup": {
                        "type": "lookup_hash",
                        "params": {
                            "autocommit": "true",
                            "from": "email",
                            "ignore_nulls": "true",
                            "table": "customers_email_lookup",
                            "to": "keyspace_id",
                            "write_only": "true"
                        },
                        "owner": "customers"
                    }
                },
                "tables": {}
            }
        """
      )

    val exception = assertThrows<VitessTestDbSchemaLintException> { linter.lint(vschemaJson, "sharded") }
    assertEquals(
      "The lookup vindex `customers_email_lookup` in keyspace `sharded` has an invalid " +
        "`to` parameter of `keyspace_id`,  as `keyspace_id` is reserved for new lookup types.",
      exception.message,
    )
  }

  @Test
  fun `test sharded vschema with valid lookup vindex`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
            {
                "sharded": true,
                "vindexes": {
                    "customers_email_lookup": {
                        "type": "lookup",
                        "params": {
                            "autocommit": "true",
                            "from": "email",
                            "ignore_nulls": "true",
                            "table": "customers_email_lookup",
                            "to": "keyspace_id"
                        },
                        "owner": "customers"
                    }
                },
                "tables": {}
            }
        """
      )!!

    assertDoesNotThrow { linter.lint(vschemaJson, "sharded") }
  }

  @Test
  fun `test sharded vschema with invalid lookup vindex`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
            {
                "sharded": true,
                "vindexes": {
                    "my_lookup": {
                        "type": "lookup",
                        "params": {
                            "table": "my_lookup",
                            "from": "id"
                        }
                    }
                },
                "tables": {}
            }
        """
      )!!

    val exception = assertThrows<VitessTestDbSchemaLintException> { linter.lint(vschemaJson, "sharded") }
    assertEquals(
      "The fields in the `lookup` vindex `my_lookup` in the vschema of `sharded` must be ordered as: [type, params, owner]",
      exception.message,
    )
  }

  @Test
  fun `test sharded vschema with table with missing column_vindexes`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
        {
            "sharded": true,
            "vindexes": {
                "hash": {
                    "type": "hash"
                }
            },
            "tables": {
                "customer_lookup": {}
            }
        }
        """
          .trimIndent()
      )!!

    val exception = assertThrows<VitessTestDbSchemaLintException> { linter.lint(vschemaJson, "sharded") }
    assertEquals(
      "The table `customer_lookup` in the vschema of `sharded` must contain a `column_vindexes` field",
      exception.message,
    )
  }

  @Test
  fun `test sharded vschema with table with invalid field count`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
        {
            "sharded": true,
            "vindexes": {
                "hash": {
                    "type": "hash"
                }
            },
            "tables": {
                 "customer_lookup": {
                    "column_vindexes": [
                        {
                            "column": "id",
                            "name": "hash"
                        }
                    ],
                    "auto_increment": {},
                    "extra": {}
                }
            }
        }
        """
          .trimIndent()
      )!!

    val exception = assertThrows<VitessTestDbSchemaLintException> { linter.lint(vschemaJson, "sharded") }
    assertEquals(
      "The table `customer_lookup` in the vschema of `sharded` can only have the fields `column_vindexes` and `auto_increment`",
      exception.message,
    )
  }

  @Test
  fun `test sharded vschema with table that can omit auto_increment`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
        {
            "sharded": true,
            "vindexes": {
                "hash": {
                    "type": "hash"
                }
            },
            "tables": {
                "customer_lookup": {
                    "column_vindexes": [
                        {
                            "column": "id",
                            "name": "hash"
                        }
                    ]
                }
            }
        }
        """
          .trimIndent()
      )!!

    assertDoesNotThrow { linter.lint(vschemaJson, "sharded") }
  }

  @Test
  fun `test sharded vschema with valid column vindexes`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
            {
                "sharded": true,
                "vindexes": {
                    "hash": {"type": "hash"}
                },
                "tables": {
                    "customers": {
                        "column_vindexes": [
                            {
                                "column": "id",
                                "name": "hash"
                            }
                        ],
                        "auto_increment": {
                            "column": "id",
                            "sequence": "customers_seq"
                        }
                    }
                }
            }
        """
      )!!

    assertDoesNotThrow { linter.lint(vschemaJson, "sharded") }
  }

  @Test
  fun `test sharded vschema with missing column_vindexes fields`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
            {
                "sharded": true,
                "vindexes": {},
                "tables": {
                    "customers": {
                        "column_vindexes": [
                            {
                                "column": "id"
                            }
                        ]
                    }
                }
            }
        """
      )!!

    val exception = assertThrows<VitessTestDbSchemaLintException> { linter.lint(vschemaJson, "sharded") }
    assertEquals(
      "The fields in the `column_vindex` in table `customers` at index `0` in the vschema of `sharded` must be ordered as `[column, name]`",
      exception.message,
    )
  }

  @Test
  fun `test sharded vschema with undefined column_vindex name`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
            {
                "sharded": true,
                "vindexes": {},
                "tables": {
                    "customers": {
                        "column_vindexes": [
                            {
                                "column": "id",
                                "name": "hash"
                            }
                        ],
                        "auto_increment": {
                          "column": "id",
                          "sequence": "customers_seq"
                        }
                    }
                }
            }
        """
      )!!

    val exception = assertThrows<VitessTestDbSchemaLintException> { linter.lint(vschemaJson, "sharded") }
    assertEquals(
      "The `column_vindex` name `hash` for table `customers` at index `0` in the vschema of `sharded` is not defined in `vindexes`",
      exception.message,
    )
  }

  @Test
  fun `test sharded vschema with invalid auto increment`() {
    val vschemaJson =
      vschemaAdapter.fromJson(
        """
            {
                "sharded": true,
                "vindexes": {
                    "hash": {"type": "hash"}
                },
                "tables": {
                    "customers": {
                        "column_vindexes": [
                            {
                                "column": "id",
                                "name": "hash"
                            }
                        ],
                        "auto_increment": {
                            "column": "id"
                        }
                    }
                }
            }
        """
      )!!

    val exception = assertThrows<VitessTestDbSchemaLintException> { linter.lint(vschemaJson, "sharded") }
    assertEquals(
      "The `auto_increment` fields in table `customers` in the vschema of `sharded` must be ordered as `[column, sequence]`",
      exception.message,
    )
  }
}

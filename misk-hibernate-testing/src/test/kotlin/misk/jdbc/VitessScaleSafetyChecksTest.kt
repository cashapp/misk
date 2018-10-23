package misk.jdbc

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VitessScaleSafetyChecksTest {
  val queryPlans = """Length: 2
"select dbmovie0_.id as id1_2_, dbmovie0_.created_at as created_2_2_, dbmovie0_.name as name3_2_, dbmovie0_.release_date as release_4_2_, dbmovie0_.updated_at as updated_5_2_ from movies as dbmovie0_ where dbmovie0_.release_date < :v1 limit :v2"
{
  "Original": "select dbmovie0_.id as id1_2_, dbmovie0_.created_at as created_2_2_, dbmovie0_.name as name3_2_, dbmovie0_.release_date as release_4_2_, dbmovie0_.updated_at as updated_5_2_ from movies as dbmovie0_ where dbmovie0_.release_date \u003c :v1 limit :v2",
  "Instructions": {
    "Opcode": "Limit",
    "Count": ":v2",
    "Offset": null,
    "Input": {
      "Opcode": "SelectScatter",
      "Keyspace": {
        "Name": "movies",
        "Sharded": true
      },
      "Query": "select dbmovie0_.id as id1_2_, dbmovie0_.created_at as created_2_2_, dbmovie0_.name as name3_2_, dbmovie0_.release_date as release_4_2_, dbmovie0_.updated_at as updated_5_2_ from movies as dbmovie0_ where dbmovie0_.release_date \u003c :v1 limit :__upper_limit",
      "FieldQuery": "select dbmovie0_.id as id1_2_, dbmovie0_.created_at as created_2_2_, dbmovie0_.name as name3_2_, dbmovie0_.release_date as release_4_2_, dbmovie0_.updated_at as updated_5_2_ from movies as dbmovie0_ where 1 != 1"
    }
  },
  "ExecCount": 1,
  "ExecTime": 48358900,
  "ShardQueries": 2,
  "Rows": 0,
  "Errors": 0
}

"select :vtg1 from dual"
{
  "Original": "select :vtg1 from dual",
  "Instructions": {
    "Opcode": "SelectUnsharded",
    "Keyspace": {
      "Name": "actors",
      "Sharded": false
    },
    "Query": "select :vtg1 from dual",
    "FieldQuery": "select :vtg1 from dual where 1 != 1"
  },
  "ExecCount": 1,
  "ExecTime": 1840700,
  "ShardQueries": 1,
  "Rows": 1,
  "Errors": 0
}
"""

  @Test fun parseQueryPlans() {
    val detector = VitessScaleSafetyChecks(
        OkHttpClient(),
        Moshi.Builder().build(),
        DataSourceConfig(type = DataSourceType.VITESS),
        StartVitessService(DataSourceConfig(type = DataSourceType.VITESS)))

    val plans = detector.parseQueryPlans(
        Buffer().writeUtf8(queryPlans)).toList()
    assertEquals(2, plans.count())

    assertTrue(plans[0].isScatter)
    assertFalse(plans[1].isScatter)
  }
}
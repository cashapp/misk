package misk.policy.opa

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Query
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

interface OpaApi {
  var provenance: Boolean

  @Headers("Content-Type: application/json")
  @POST("/v1/data/{documentPath}")
  fun queryDocument(
    @Path(value = "documentPath", encoded = true) documentPath: String,
    @Body input: String,
    @Query("provenance") provenance: Boolean = false
  ): Call<ResponseBody>
}

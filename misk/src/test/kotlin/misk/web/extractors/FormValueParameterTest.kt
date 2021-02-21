package misk.web.extractors

import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.FormField
import misk.web.FormValue
import misk.web.Post
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class FormValueParameterTest {
  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var jettyService: JettyService

  @Test fun basicParams() {
    val list = listOf(
      Pair("str", "foo"), Pair("other", "stuff"), Pair("int", "12"),
      Pair("testEnum", "ONE")
    )
    assertThat(post("/basic-params", list))
      .isEqualTo("BasicForm(str=foo, other=stuff, int=12, testEnum=ONE)")
  }

  @Test fun optionalParamsPresent() {
    val list = listOf(Pair("str", "foo"), Pair("int", "12"))
    assertThat(post("/optional-params", list))
      .isEqualTo("OptionalForm(str=foo, int=12)")
  }

  @Test fun optionalParamsNotPresent() {
    assertThat(post("/optional-params", listOf()))
      .isEqualTo("OptionalForm(str=null, int=null)")
  }

  @Test fun defaultParamsPresent() {
    val list = listOf(Pair("str", "foo"), Pair("int", "12"), Pair("testEnum", "ONE"))
    assertThat(post("/default-params", list))
      .isEqualTo("DefaultForm(str=foo, int=12, testEnum=ONE)")
  }

  @Test fun defaultParamsNotPresent() {
    assertThat(post("/default-params", listOf()))
      .isEqualTo("DefaultForm(str=square, int=23, testEnum=TWO)")
  }

  @Test fun listParams() {
    val list = listOf(
      Pair("strs", "foo"), Pair("strs", "bar"), Pair("ints", "12"), Pair("ints", "42"),
      Pair("strs", "baz")
    )

    assertThat(post("/list-params", list))
      .isEqualTo("ListForm(strs=[foo, bar, baz], ints=[12, 42])")
  }

  @Test fun ignoresAdditionalParameters() {
    val list = listOf(
      Pair("str", "foo"), Pair("other", "stuff"), Pair("int", "12"),
      Pair("testEnum", "ONE"), Pair("not present", "value")
    )

    assertThat(post("/basic-params", list))
      .isEqualTo("BasicForm(str=foo, other=stuff, int=12, testEnum=ONE)")
  }

  @Test fun caseInsensitive() {
    val list = listOf(
      Pair("str", "foo"), Pair("OTHER", "stuff"), Pair("InT", "12"),
      Pair("tEsTeNuM", "ONE")
    )

    assertThat(post("/basic-params", list))
      .isEqualTo("BasicForm(str=foo, other=stuff, int=12, testEnum=ONE)")
  }

  @Test fun formValueAnnotation() {
    val list = listOf(Pair("user-name", "user123"))

    assertThat(post("/form-value-annotation", list))
      .isEqualTo("AnnotationForm(username=user123)")
  }

  enum class TestEnum {
    ONE,
    TWO
  }

  class BasicParamsAction @Inject constructor() : WebAction {
    @Post("/basic-params")
    fun call(@FormValue basicForm: BasicForm) = "$basicForm"

    data class BasicForm(
      val str: String,
      val other: String,
      val int: Int,
      val testEnum: TestEnum
    )
  }

  class OptionalParamsAction @Inject constructor() : WebAction {
    @Post("/optional-params")
    fun call(@FormValue optionalForm: OptionalForm) = "$optionalForm"

    data class OptionalForm(
      val str: String?,
      val int: Int?
    )
  }

  class DefaultParamsAction @Inject constructor() : WebAction {
    @Post("/default-params")
    fun call(@FormValue defaultForm: DefaultForm) = "$defaultForm"

    data class DefaultForm(
      val str: String = "square",
      val int: Int = 23,
      val testEnum: TestEnum = TestEnum.TWO
    )
  }

  class ListParamsAction @Inject constructor() : WebAction {
    @Post("/list-params")
    fun call(@FormValue listForm: ListForm) = "$listForm"

    data class ListForm(
      val strs: List<String>,
      val ints: List<Int>
    )
  }

  class FormValueAnnotationAction @Inject constructor() : WebAction {
    @Post("/form-value-annotation")
    fun call(@FormValue annotationForm: AnnotationForm) = "$annotationForm"

    data class AnnotationForm(
      @FormField("user-name") val username: String
    )
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebActionModule.create<BasicParamsAction>())
      install(WebActionModule.create<OptionalParamsAction>())
      install(WebActionModule.create<DefaultParamsAction>())
      install(WebActionModule.create<ListParamsAction>())
      install(WebActionModule.create<FormValueAnnotationAction>())
    }
  }

  private fun post(
    path: String,
    body: List<Pair<String, String>>
  ): String {
    val url = jettyService.httpServerUrl.newBuilder()
      .encodedPath(path)
      .build()

    val builder = FormBody.Builder()
    body.forEach { kv -> builder.add(kv.first, kv.second) }

    val request = Request.Builder()
      .url(url)
      .post(builder.build())

    return call(request)
  }

  private fun call(request: Request.Builder): String {
    val httpClient = OkHttpClient()
    val response = httpClient.newCall(request.build())
      .execute()
    assertThat(response.code)
      .isEqualTo(200)
    return response.body!!.source()
      .readUtf8()
  }
}

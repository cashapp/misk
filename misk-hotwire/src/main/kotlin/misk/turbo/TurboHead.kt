package misk.turbo

import kotlinx.html.TagConsumer
import kotlinx.html.meta
import kotlinx.html.script

/** HTML tags to add to Head to configure Hotwire (Turbo, Stimulus) dependencies and default controllers */
fun TagConsumer<*>.addHotwireHeadImports(appRoot: String) {
  // Install Dependencies
  script {
    // Cache downloaded from:
    // src = "https://unpkg.com/@hotwired/turbo@7.2.5/dist/turbo.es2017-umd.js"
    src = "/static/cache/turbo/7.2.5/es2017-umd.min.js"
  }

  // Install all Stimulus Controllers
  val controllers = listOf("hello_controller", "toggle_click_outside_controller", "toggle_controller")
  controllers.forEach {
    script {
      type = "module"
      src = "/static/controllers/$it.js"
    }
  }

  // Setup Turbo
  meta {
    name = "turbo-root"
    content = appRoot
  }
  script {
    type = "text/javascript"
    """
    |if (window["EventSource"] && window["Turbo"]) {
    |   Turbo.connectStreamSource(new EventSource("/load"));
    |} else {
    |    console.warn("Turbo Streams over SSE not available");
    |}
    """
      .trimMargin()
  }
}

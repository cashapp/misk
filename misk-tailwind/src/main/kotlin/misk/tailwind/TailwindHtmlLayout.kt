package misk.tailwind

import kotlinx.html.TagConsumer
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.script
import kotlinx.html.title
import misk.turbo.addHotwireHeadImports


internal class DevMode {
  companion object {
    val devMode by lazy {
      System.getProperty("misk.dev.running") == "true"
    }
  }
}

fun TagConsumer<*>.TailwindHtmlLayout(appRoot: String, title: String, playCdn: Boolean = false, appCssPath: String? = null, headBlock: TagConsumer<*>.() -> Unit = {}, bodyBlock: TagConsumer<*>.() -> Unit) {
  html {
    attributes["class"] = "h-full bg-white"
    head {
      meta {
        charset = "utf-8"
      }
      meta {
        name = "viewport"
        content = "width=device-width, initial-scale=1.0"
      }
      link {
        rel = "shortcut icon"
        type = "image/x-icon"
        href = "/static/favicon.ico"
      }
      // TODO add Gradle plugin to comb through service JAR to build minified Tailwind CSS
      // Until then, use play CDN so all CSS is present for UI from Misk or internal libaries/services
//      if (playCdn) {
        // Play CDN is useful for development
      script {
//        src = "https://cdn.tailwindcss.com?plugins=forms,typography,aspect-ratio"
        src = "/static/cache/tailwind/3.3.5/tailwind.min.js"
      }
//      }
      appCssPath?.let { path ->
        link {
          href = path
          rel = "stylesheet"
        }
      }
      link {
        href = "/static/cache/tailwind.misk.min.css"
        rel = "stylesheet"
      }
      title(title)
      if (DevMode.devMode) {
        script {
          type = "module"
          src = "/static/js/refresh_dev.js"
        }
      }

      addHotwireHeadImports(appRoot)
      headBlock()
    }
    body(classes = "h-full") {
      bodyBlock()
    }
  }
}



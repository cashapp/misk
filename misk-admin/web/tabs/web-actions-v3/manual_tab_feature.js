// TODO finish this handwritten impl to setup necessary Hotwire dependencies and get rid of the React Misk-Web wrapper

// Calculate the current location path
var e = window
var t =
  "" +
  ((e.location && e.location.search && e.location.search + "&") || "?") +
  ((e.location && e.location.pathname && "path=" + e.location.pathname) || "")

// Add Hotwire dependencies to <head />
document.getElementsByTagName("head")[0].appendChild(
  document.createElement("meta", {
    name: "turbo-visit-control",
    content: "reload"
  })
)
document.getElementsByTagName("head")[0].appendChild(
  document.createElement("meta", {
    name: "viewport",
    content: "width=device-width, initial-scale=1.0"
  })
)
document.getElementsByTagName("head")[0].appendChild(
  document.createElement("link", {
    href: "/static/cache/tailwind.min.css",
    rel: "stylesheet"
  })
)
document.getElementsByTagName("head")[0].appendChild(
  document.createElement("link", {
    href: "/static/cache/fontawesome.min.css",
    rel: "stylesheet",
    integrity:
      "sha384-jLKHWM3JRmfMU0A5x5AkjWkw/EYfGUAGagvnfryNV3F9VqM98XiIH7VBGVoxVSc7",
    crossOrigin: "anonymous"
  })
)
document
  .getElementsByTagName("head")[0]
  .appendChild(
    document.createElement("script", { src: "/static/cache/turbo.es5-umd.js" })
  )
document.getElementsByTagName("head")[0].appendChild(
  document.createElement("script", {
    type: "module",
    src: "/static/js/misk_db_feature_search_form_controller.js"
  })
)
document
  .getElementsByTagName("head")[0]
  .appendChild(
    document.createElement(
      "script",
      { type: "text/javascript" },
      'if (window["EventSource"] && window["Turbo"]) {\n               Turbo.connectStreamSource(new EventSource("/load"));\n            } else {\n                console.warn("Turbo Streams over SSE not available");\n            }'
    )
  )

// Add to div feature anchor provided by Misk Admin Dashboard
document.getElementById("feature").appendChild(
  document.createElement(
    "div",
    null,
    document.createElement("h1", null, "Feature"),
    document.createElement("div", {
      dangerouslySetInnerHTML: {
        __html:
          '<turbo-frame id="tab-root" src="/misk.db.feature.web/TabContainer' +
          t +
          '"></turbo-frame>'
      }
    })
  )
)

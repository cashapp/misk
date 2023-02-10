import { H1 } from "@blueprintjs/core"
import * as React from "react"
import { Helmet } from "react-helmet"
import { connect } from "react-redux"
import {
  IDispatchProps,
  IState,
  mapDispatchToProps,
  mapStateToProps
} from "src/ducks"

const TabContainer = (props: IState & IDispatchProps) => {
  const query =
    (props.location && props.location.search && `${props.location.search}&`) ||
    "?"
  const path =
    (props.location &&
      props.location.pathname &&
      `path=${props.location.pathname}`) ||
    ""
  const encoded = `${query}${path}`

  return (
    <div>
      <Helmet>
        <meta name="turbo-visit-control" content="reload" />
        <meta
          name={"viewport"}
          content={"width=device-width, initial-scale=1.0"}
        />
        <link
          //           href={"https://unpkg.com/tailwindcss@^2/dist/tailwind.min.css"}
          href={"/static/cache/tailwind.min.css"}
          rel={"stylesheet"}
        />
        <link
          //           href={ "https://cdn.jsdelivr.net/npm/@fortawesome/fontawesome-free@5.15.4/css/fontawesome.min.css" }
          href={"/static/cache/fontawesome.min.css"}
          rel={"stylesheet"}
          integrity={
            "sha384-jLKHWM3JRmfMU0A5x5AkjWkw/EYfGUAGagvnfryNV3F9VqM98XiIH7VBGVoxVSc7"
          }
          crossOrigin={"anonymous"}
        />
        <script
          //           src={ "https://unpkg.com/@hotwired/turbo@7.0.0-beta.3/dist/turbo.es5-umd.js" }
          src={"/static/cache/turbo.es5-umd.js"}
        />
        <script
          type={"module"}
          src={"/static/js/misk_db_feature_search_form_controller.js"}
        />
        <script type={"text/javascript"}>
          {`if (window["EventSource"] && window["Turbo"]) {
               Turbo.connectStreamSource(new EventSource("/load"));
            } else {
                console.warn("Turbo Streams over SSE not available");
            }`}
        </script>
      </Helmet>
      <div>
        <H1>Web Actions</H1>
        <div
          dangerouslySetInnerHTML={{
            __html: `<turbo-frame id="tab-root" src="/misk.db.feature.web/TabContainer${encoded}"></turbo-frame>`
          }}
        />
      </div>
    </div>
  )
}

export default connect(mapStateToProps, mapDispatchToProps)(TabContainer)

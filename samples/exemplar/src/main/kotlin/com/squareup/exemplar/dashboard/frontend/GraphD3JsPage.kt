package com.squareup.exemplar.dashboard.frontend

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.h1
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.unsafe
import misk.hotwire.buildHtml
import misk.turbo.turbo_frame
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.dashboard.HtmlLayout
import misk.web.mediatype.MediaTypes

/** Example page that shows usage of D3.js graph library https://d3js.org/ */
@Singleton
class GraphD3JsPage @Inject constructor() : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(): String {
    return buildHtml {
      HtmlLayout(
        appRoot = "/app",
        title = "Graph Example",
        playCdn = false,
        headBlock = {
          script {
            type = "text/javascript"
            src = "https://cdn.jsdelivr.net/npm/d3@7"
          }
        },
      ) {
        turbo_frame(id = "tab") {
          h1("text-xl") { +"""Graph Example""" }
          p("pb-10") {
            +"""Below is an example graph built with D3.js, see the Misk samples/exemplar source code for how."""
          }

          script {
            type = "text/javascript"
            unsafe {
              raw(
                """
                //Width and height
                var w = 500;
                var h = 100;

                var dataset = [
                        [5, 20], [480, 90], [250, 50], [100, 33], [330, 95],
                        [410, 12], [475, 44], [25, 67], [85, 21], [220, 88]
                        ];

                //Create SVG element
                var svg = d3.select("body")
                      .append("svg")
                      .attr("width", w)
                      .attr("height", h);

                svg.selectAll("circle")
                   .data(dataset)
                   .enter()
                   .append("circle")
                   .attr("cx", function(d) {
                      return d[0];
                   })
                   .attr("cy", function(d) {
                      return d[1];
                   })
                   .attr("r", function(d) {
                      return Math.sqrt(h - d[1]);
                   });

                svg.selectAll("text")
                   .data(dataset)
                   .enter()
                   .append("text")
                   .text(function(d) {
                      return d[0] + "," + d[1];
                   })
                   .attr("x", function(d) {
                      return d[0];
                   })
                   .attr("y", function(d) {
                      return d[1];
                   })
                   .attr("font-family", "sans-serif")
                   .attr("font-size", "11px")
                   .attr("fill", "red");
                """
                  .trimIndent()
              )
            }
          }
        }
      }
    }
  }

  companion object {
    const val PATH = "/ui/example/graph-d3-js"
  }
}

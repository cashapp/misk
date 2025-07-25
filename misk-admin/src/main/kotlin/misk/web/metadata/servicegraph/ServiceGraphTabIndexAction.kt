package misk.web.metadata.servicegraph

import com.google.inject.Provider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.script
import kotlinx.html.unsafe
import misk.metadata.servicegraph.ServiceGraphMetadata
import misk.moshi.adapter
import misk.tailwind.components.AlertInfo
import misk.tailwind.components.AlertInfoHighlight
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import misk.web.metadata.toFormattedJson
import misk.web.v2.DashboardPageLayout
import wisp.moshi.ProviderJsonAdapterFactory
import wisp.moshi.buildMoshi

@Singleton
class ServiceGraphTabIndexAction @Inject constructor(
  private val dashboardPageLayout: DashboardPageLayout,
  private val serviceGraphMetadataProvider: Provider<ServiceGraphMetadata>,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(): String = dashboardPageLayout
    .newBuilder()
    .headBlock {
      script {
        src = "https://cdn.jsdelivr.net/npm/d3@7"
        type = "text/javascript"
      }
    }
    .build { _, _, _ ->
      val metadataArray = buildMoshi(listOf(ProviderJsonAdapterFactory()))
        .adapter<List<ServiceGraphMetadata.GraphPairs>>()
        .toFormattedJson(serviceGraphMetadataProvider.get().graphVisual)

      div("container mx-auto p-8") {
        h1("text-3xl font-bold mb-8") {
          +"""Service Graph"""
        }
        AlertInfoHighlight("Explore the directed Guava service graph for your application.  If the graph doesn't show below, try reloading the page.", "Guava Docs", "https://github.com/google/guava/wiki/ServiceExplained")
        AlertInfo("Understanding the Graph: A directed arrow from Service A to Service B shows that Service B requires Service A to already be running before it can start. Services that always need to be running are configured to be required by the misk.ReadyService.")

        div("svg-container") { }

        // JavaScript code in a block
        script {
          unsafe {
            +"""
                var metadata = $metadataArray;
                
                var linkColor = "steelblue" // Define a single color for all links
                
                drag = simulation => {
                  
                  function dragstarted(event, d) {
                      if (!event.active) simulation.alphaTarget(0.3).restart();
                      d.fx = d.x;
                      d.fy = d.y;
                  }
                    
                  function dragged(event, d) {
                      d.fx = event.x;
                      d.fy = event.y;
                  }
                    
                  function dragended(event, d) {
                      if (!event.active) simulation.alphaTarget(0);
                      d.fx = null;
                      d.fy = null;
                  }
                  
                  return d3.drag()
                      .on("start", dragstarted)
                      .on("drag", dragged)
                      .on("end", dragended);
                }
                
                function linkArc(d) {
                  const r = Math.hypot(d.target.x - d.source.x, d.target.y - d.source.y);
                  return "M" + d.source.x + "," +  d.source.y + "A" + r + "," + r + " 0 0,1 " + d.target.x + "," + d.target.y;
                }
              
                var width = 1600;
                var height = 1000;
                var margin = 500;
                var nodes = Array.from(new Set(metadata.flatMap(l => [l.source, l.target])), id => ({id}));
                var links = metadata.map(d => Object.create(d))
                
                // Set initial positions for the nodes
                nodes.forEach(node => {
                  node.x = Math.random() * width + margin;
                  node.y = Math.random() * height + margin;
                });
                            
                var simulation = d3.forceSimulation(nodes)
                    .force("link", d3.forceLink(links).id(d => d.id).distance(200))
                    .force("charge", d3.forceManyBody().strength(-2000))
                    .force("x", d3.forceX().strength(0.1))
                    .force("y", d3.forceY().strength(0.1)); 
              
                var svg = d3.select(".svg-container").append("svg")
                    .attr("viewBox", [-margin, -margin, width, height])
                    .attr("width", "100%")
                    .attr("height", "100%")
                    .attr("style", "max-width: 100%; height: auto; font: 18px sans-serif;");
                
                // Defines arrows on the links
                svg.append("defs").selectAll("marker")
                    .data(["link"])
                    .join("marker")
                    .attr("id", d => "arrow-" + d)
                    .attr("viewBox", "0 -5 10 10")
                    .attr("refX", 15)
                    .attr("refY", -0.5)
                    .attr("markerWidth", 6)
                    .attr("markerHeight", 6)
                    .attr("orient", "auto")
                    .append("path")
                    .attr("fill", linkColor)
                    .attr("d", "M0,-5L10,0L0,5");
              
                // Defines links between nodes
                var link = svg.append("g")
                    .attr("fill", "none")
                    .attr("stroke-width", 2.5)
                    .selectAll("path")
                    .data(links)
                    .join("path")
                    .attr("stroke", linkColor)
                    .attr("marker-end", d => "url(#arrow-link)");
              
                // Defines actual nodes
                var node = svg.append("g")
                    .attr("fill", "currentColor")
                    .attr("stroke-linecap", "round")
                    .attr("stroke-linejoin", "round")
                    .selectAll("g")
                    .data(nodes)
                    .join("g")
                    .call(drag(simulation));
              
                node.append("circle")
                    .attr("stroke", "white")
                    .attr("stroke-width", 1.5)
                    .attr("r", 4);
              
                node.append("text")
                    .attr("x", 12)
                    .attr("y", "0.31em")
                    .text(d => d.id)
                    .clone(true).lower()
                    .attr("fill", "none")
                    .attr("stroke", "white")
                    .attr("stroke-width", 3);
              
                simulation.on("tick", () => {
                  link.attr("d", linkArc);
                  node.attr("transform", d => "translate(" + d.x + "," + d.y + ")");
                });
                
                Object.assign(svg.node(), {scales: {linkColor}});
            """.trimIndent()
          }
        }
      }
    }

  companion object {
    const val PATH = "/_admin/service-graph/"
  }
}

const path = require("path")
const { BundleAnalyzerPlugin } = require("webpack-bundle-analyzer")

module.exports = {
  name: "vendors",
  mode: "production",
  entry: {
    vendors: path.resolve(__dirname, "src/vendors.js")
  },
  devtool: "source-map",
  output: {
    path: path.resolve(__dirname, "./lib/web/@misk/common"),
    filename: "[name].js",
    library: ["Misk", "Common"],
    libraryTarget: "umd",
    /**
     * library will try to bind to browser `window` variable
     * without below globalObject: library binding to browser `window`
     *    fails when run in Node or other non-browser
     */
    globalObject: "typeof window !== 'undefined' ? window : this"
  },
  plugins: [
    new BundleAnalyzerPlugin({
      analyzerMode: "static",
      reportFilename: "bundle-analyzer-report-vendors.html",
      statsFilename: "bundle-analyzer-report-vendors.json",
      generateStatsFile: true,
      openAnalyzer: false
    })
  ]
}

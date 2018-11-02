const path = require("path")
const { BundleAnalyzerPlugin } = require("webpack-bundle-analyzer")

const bundleAnalyzer = false

module.exports = {
  name: "library",
  mode: "production",
  entry: {
    common: path.resolve(__dirname, "src/index.ts")
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
  module: {
    rules: [
      {
        test: /\.(ts|tsx)?$/,
        loader: "awesome-typescript-loader"
      }
    ]
  },
  resolve: {
    extensions: [".ts", ".tsx"]
  },
  plugins: bundleAnalyzer
    ? [
        new BundleAnalyzerPlugin({
          analyzerMode: "static",
          reportFilename: "bundle-analyzer-report-common.html",
          statsFilename: "bundle-analyzer-report-common.json",
          generateStatsFile: true,
          openAnalyzer: false
        })
      ]
    : []
}

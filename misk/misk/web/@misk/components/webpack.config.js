const path = require("path")
const MiskDev = require("@misk/dev")
const StyledComponentsTransformerPlugin = require("typescript-plugin-styled-components")
const createStyledComponentsTransformer =
  StyledComponentsTransformerPlugin.default
const { BundleAnalyzerPlugin } = require("webpack-bundle-analyzer")

const bundleAnalyzer = false

const StyledComponentsTransformer = createStyledComponentsTransformer()

module.exports = {
  mode: "production",
  entry: {
    components: path.resolve(__dirname, "src/index.ts")
  },
  devtool: "source-map",
  output: {
    path: path.resolve(__dirname, "./lib/web/@misk/components"),
    filename: "[name].js",
    library: ["Misk", "Components"],
    libraryTarget: "umd",
    /**
     * library will try to bind to browser `window` variable
     * without below globalObject: library binding to browser `window`
     *    fails when run in Node or other non-browser
     */
    globalObject: "typeof self !== 'undefined' ? self : this"
  },
  module: {
    rules: [
      {
        test: /\.tsx?$/,
        loader: "awesome-typescript-loader",
        options: {
          getCustomTransformers: () => ({
            before: [StyledComponentsTransformer]
          })
        }
      },
      {
        test: /\.(scss|sass|css)$/,
        loader: ["style-loader", "css-loader?minimize=true", "sass-loader"]
      }
    ]
  },
  resolve: {
    extensions: [".ts", ".tsx", ".js", ".jsx", ".json"]
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
    : [],
  externals: MiskDev.vendorExternals
}

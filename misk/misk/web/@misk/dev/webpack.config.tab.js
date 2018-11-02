const { vendorExternals, miskExternals } = require("./externals")
const CopyWebpackPlugin = require("copy-webpack-plugin")
const HTMLWebpackPlugin = require("html-webpack-plugin")
const HTMLWebpackHarddiskPlugin = require("html-webpack-harddisk-plugin")
const path = require("path")
const webpack = require("webpack")
const { BundleAnalyzerPlugin } = require("webpack-bundle-analyzer")
const merge = require("webpack-merge")

module.exports = (env, argv, otherConfigFields = {}) => {
  const { dirname, miskTab, bundleAnalyzer = false } = argv

  if ("name" in miskTab && "port" in miskTab && "slug" in miskTab) {
    console.log("[MISK] Valid miskTab in package.json")
  } else {
    console.log(
      "[MISK] Invalid miskTab in package.json, testing for missing fields..."
    )
    let errMsg = "\n"
    errMsg +=
      "name" in miskTab
        ? "[MISK] miskTab contains name\n"
        : "[MISK] miskTab missing name\n"
    errMsg +=
      "port" in miskTab
        ? "[MISK] miskTab contains port\n"
        : "[MISK] miskTab missing port\n"
    errMsg +=
      "slug" in miskTab
        ? "[MISK] miskTab contains slug\n"
        : "[MISK] miskTab missing slug\n"
    throw Error(errMsg)
  }

  const { name, port, slug } = miskTab
  const relative_path_prefix = miskTab.relative_path_prefix
    ? miskTab.relative_path_prefix
    : `_tab/${miskTab.slug}/`
  const output_path = miskTab.output_path
    ? miskTab.output_path
    : `lib/web/_tab/${slug}`

  const DefinePluginConfig = new webpack.DefinePlugin({
    "process.env.NODE_ENV": JSON.stringify("production")
  })

  const HTMLWebpackPluginConfig = new HTMLWebpackPlugin({
    slug: `${slug}`,
    template: path.join(dirname, "/src/index.html"),
    filename: `index.html`,
    inject: "body",
    alwaysWriteToDisk: true
  })

  const HTMLWebpackHarddiskPluginConfig = new HTMLWebpackHarddiskPlugin()

  const CopyWebpackPluginConfig = new CopyWebpackPlugin(
    [
      { from: "./node_modules/@misk/common/lib/web/" },
      { from: "./node_modules/@misk/components/lib/web/" }
    ],
    { copyUnmodified: true }
  )

  const BundleAnalyzerPluginConfig = new BundleAnalyzerPlugin({
    analyzerMode: "static",
    reportFilename: `bundle-analyzer-report-${slug}.html`,
    statsFilename: `bundle-analyzer-report-${slug}.json`,
    generateStatsFile: true,
    openAnalyzer: false
  })

  const baseConfigFields = {
    entry: {
      [`${relative_path_prefix}tab_${slug}`]: [
        "react-hot-loader/patch",
        path.join(dirname, "/src/index.tsx")
      ], // two locations so local dev and through misk proxy works
      [`tab_${slug}`]: [
        "react-hot-loader/patch",
        path.join(dirname, "/src/index.tsx")
      ]
    },
    output: {
      filename: `[name].js`,
      path: path.join(dirname, output_path),
      publicPath: "/",
      library: ["MiskTabs", name],
      libraryTarget: "umd",
      /**
       * library will try to bind to browser `window` variable
       * without below globalObject: library binding to browser `window`
       *    fails when run in Node or other non-browser
       */
      globalObject: "typeof self !== 'undefined' ? self : this"
    },
    devServer: {
      host: "0.0.0.0",
      port: port,
      inline: true,
      hot: true,
      historyApiFallback: true
    },
    module: {
      rules: [
        {
          test: /\.(tsx|ts)$/,
          exclude: /node_modules/,
          loaders: "awesome-typescript-loader"
        },
        {
          enforce: "pre",
          test: /\.js$/,
          loader: "source-map-loader"
        },
        {
          test: /\.scss$/,
          loader: "style-loader!css-loader!sass-loader"
        },
        {
          test: /\.(jpe?g|png|gif|svg)$/i,
          loader: "url-loader",
          options: {
            limit: 10000
          }
        }
      ]
    },
    resolve: {
      extensions: [".js", ".jsx", ".ts", ".tsx", ".json"]
    },
    mode: env !== "production" ? "development" : "production",
    plugins: [
      CopyWebpackPluginConfig,
      HTMLWebpackPluginConfig,
      HTMLWebpackHarddiskPluginConfig
    ].concat(
      env !== "production"
        ? [new webpack.HotModuleReplacementPlugin()]
        : [DefinePluginConfig].concat(
            bundleAnalyzer ? [BundleAnalyzerPluginConfig] : []
          )
    ),
    externals: { ...vendorExternals, ...miskExternals }
  }

  return merge(baseConfigFields, otherConfigFields)
}

const { vendorExternals, miskExternals } = require("./externals")
const CopyWebpackPlugin = require("copy-webpack-plugin")
const HTMLWebpackPlugin = require("html-webpack-plugin")
const HTMLWebpackHarddiskPlugin = require("html-webpack-harddisk-plugin")
const StyledComponentsTransformerPlugin = require("typescript-plugin-styled-components")
const createStyledComponentsTransformer =
  StyledComponentsTransformerPlugin.default
const path = require("path")
const webpack = require("webpack")
const { BundleAnalyzerPlugin } = require("webpack-bundle-analyzer")
const merge = require("webpack-merge")

module.exports = (env, argv, otherConfigFields = {}) => {
  const { dirname, miskTab, bundleAnalyzer = false } = argv

  if (
    "name" in miskTab &&
    "port" in miskTab &&
    "slug" in miskTab &&
    "version" in miskTab
  ) {
    console.log("[MISK] Valid miskTab in package.json")
  } else {
    console.log(
      "[MISK] Invalid miskTab in package.json, testing for missing fields..."
    )
    let errMsg = "\n"
    errMsg +=
      "name" in miskTab
        ? `[MISK] miskTab contains name: ${miskTab.name}\n`
        : "[MISK] miskTab missing name\n"
    errMsg +=
      "port" in miskTab
        ? `[MISK] miskTab contains port: ${miskTab.port}\n`
        : "[MISK] miskTab missing port\n"
    errMsg +=
      "slug" in miskTab
        ? `[MISK] miskTab contains slug: ${miskTab.slug}\n`
        : "[MISK] miskTab missing slug\n"
    errMsg +=
      "version" in miskTab
        ? `[MISK] miskTab contains version: ${miskTab.version}\n`
        : "[MISK] miskTab missing version, upgrade to latest squareup/misk-web Docker image version at https://hub.docker.com/r/squareup/misk-web/\n"
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

  const BundleAnalyzerPluginConfig = new BundleAnalyzerPlugin({
    analyzerMode: "static",
    reportFilename: `bundle-analyzer-report-${slug}.html`,
    statsFilename: `bundle-analyzer-report-${slug}.json`,
    generateStatsFile: true,
    openAnalyzer: false
  })

  const CopyWebpackPluginConfig = new CopyWebpackPlugin(
    [
      { from: "./node_modules/@misk/common/lib/web/" },
      { from: "./node_modules/@misk/components/lib/web/" }
    ],
    { copyUnmodified: true }
  )

  const HTMLWebpackHarddiskPluginConfig = new HTMLWebpackHarddiskPlugin()

  const HTMLWebpackPluginConfig = new HTMLWebpackPlugin({
    slug: `${slug}`,
    template: path.join(dirname, "/src/index.html"),
    filename: `index.html`,
    inject: "body",
    alwaysWriteToDisk: true
  })

  const StyledComponentsTransformer = createStyledComponentsTransformer()

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
          loader: "awesome-typescript-loader",
          options: {
            getCustomTransformers: () => ({
              before: [StyledComponentsTransformer]
            })
          }
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

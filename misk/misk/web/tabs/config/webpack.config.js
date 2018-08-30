/**
 * Webpack Config
 * 
 * Requires the following metadata be added to the project's package.json 
  "miskTabWebpack": {
    "name": "Config",
    "slug": "config",
    "relative_path_prefix": "_tab/config/",
    "port": "3200"
  }
 *
 * Assumes following project structure
 *  dist/ – output for Webpack builds
 *  src/
 *     index.tsx – entry point for Webpack compilation
 *     index.html – entry for HTML browser
 *  package.json
 *  webpack.config.js – this file
 */
const validConfig = (config) => {
  return true

}

const MiskCommon = require('@misk/common')
const { MiskWebpackConfigBase } = require("@misk/webpack")

const path = require('path')
const CopyWebpackPlugin = require('copy-webpack-plugin')
const HTMLWebpackPlugin = require('html-webpack-plugin')

const MiskTabConfig = require(path.join(process.cwd(), "package.json")).miskTabWebpack
const RELATIVE_PATH = MiskTabConfig.relative_path_prefix

const HTMLWebpackPluginConfig = new HTMLWebpackPlugin({
  template: path.join(__dirname, '/src/index.html'),
  filename: 'index.html',
  inject: 'body'
})

const CopyWebpackPluginConfig = new CopyWebpackPlugin(
  [
    { from: './node_modules/@misk/common/lib', to: `${RELATIVE_PATH}@misk/`},
    { from: './node_modules/@misk/components/lib', to: `${RELATIVE_PATH}@misk/`}
  ], 
  { debug: 'info', copyUnmodified: true }
)

module.exports = {...MiskWebpackConfigBase,
  entry: ['react-hot-loader/patch', path.join(__dirname, '/src/index.tsx')],
  output: { ...MiskWebpackConfigBase.output,
    filename: `${RELATIVE_PATH}/tab_${MiskTabConfig.slug}.js`,
    path: path.join(__dirname, 'dist'),
    library: ['MiskTabs', `${MiskTabConfig.name}`],
  },
  devServer: { ...MiskWebpackConfigBase.devServer,
    port: MiskTabConfig.port
  },
  plugins: []
    .concat(MiskWebpackConfigBase.plugins)
    .concat([CopyWebpackPluginConfig, HTMLWebpackPluginConfig]),
  externals: { ...MiskCommon.externals }
}

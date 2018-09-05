const MiskCommon = require('@misk/common')
const { MiskWebpackConfigBase } = require("@misk/webpack")

const path = require('path')
const merge = require('webpack-merge')
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

module.exports = merge(MiskWebpackConfigBase, {
  entry: ['react-hot-loader/patch', path.join(__dirname, '/src/index.tsx')],
  output: {
    filename: `${RELATIVE_PATH}tab_${MiskTabConfig.slug}.js`,
    path: path.join(__dirname, 'dist'),
    library: ['MiskTabs', `${MiskTabConfig.name}`],
  },
  devServer: {
    port: MiskTabConfig.port
  },
  plugins: [CopyWebpackPluginConfig, HTMLWebpackPluginConfig],
  externals: MiskCommon.externals
})

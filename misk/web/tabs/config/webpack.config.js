const MiskWebpackConfig = require("./webpack.config.base")
const path = require('path')
const CopyWebpackPlugin = require('copy-webpack-plugin')

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

const MiskTabConfig = require(path.join(process.cwd(), "package.json")).miskTabWebpack
const RELATIVE_PATH = MiskTabConfig.relative_path_prefix

const CopyWebpackPluginConfig = new CopyWebpackPlugin(
  [
    { from: './node_modules/@misk/common/lib', to: `${RELATIVE_PATH}@misk/`},
    { from: './node_modules/@misk/components/lib', to: `${RELATIVE_PATH}@misk/`}
  ], 
  { debug: 'info', copyUnmodified: true }
)

module.exports = {...MiskWebpackConfig,
  output: { ...MiskWebpackConfig.output,
    filename: `${RELATIVE_PATH}/tab_${MiskTabConfig.slug}.js`,
    library: ['MiskTabs', `${MiskTabConfig.name}`],
  },
  devServer: { ...MiskWebpackConfig.devServer,
    port: MiskTabConfig.port
  },
  plugins: []
    .concat(MiskWebpackConfig.plugins)
    .concat([CopyWebpackPluginConfig])
}
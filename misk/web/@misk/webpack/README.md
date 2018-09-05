Misk Webpack
---
![](https://raw.githubusercontent.com/square/misk/master/misk.png)
This package provides shared Webpack build configs for use across Misk tab repos.

Getting Started
---
```bash
$ yarn add @misk/webpack
```

Webpack Child Template
---
Use the following template to consume the base Webpack config. Start by creating a file `webpack.config.js` in your repo main directory and copying in the following.

```Javascript
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
```

Package.json Input Parameters
---
The child Webpack template above consumes some static initialization variables that you must add to your `package.json`. An example for the Config tab is included below.

```JSON
  "devDependencies": {
    "@misk/dev": "^0.0.10"
  },
  "miskTabWebpack": {
    "name": "Config",
    "slug": "config",
    "relative_path_prefix": "_tab/config/",
    "port": "3000"
  }
```

[Releasing](https://github.com/square/misk/blob/master/misk/web/%40misk/RELEASING.md)
---
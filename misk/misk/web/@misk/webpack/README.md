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
const { MiskWebpackConfigBase } = require("@misk/webpack")
const path = require('path')
const CopyWebpackPlugin = require('copy-webpack-plugin')

const MiskTabConfig = require(path.join(process.cwd(), "package.json")).miskTabWebpack
const RELATIVE_PATH = MiskTabConfig.relative_path_prefix

const CopyWebpackPluginConfig = new CopyWebpackPlugin(
  [
    { from: './node_modules/@misk/common/lib', to: `${RELATIVE_PATH}@misk/`},
    { from: './node_modules/@misk/components/lib', to: `${RELATIVE_PATH}@misk/`}
  ], 
  { debug: 'info', copyUnmodified: true }
)

module.exports = {...MiskWebpackConfigBase,
  output: { ...MiskWebpackConfigBase.output,
    filename: `${RELATIVE_PATH}/tab_${MiskTabConfig.slug}.js`,
    library: ['MiskTabs', `${MiskTabConfig.name}`],
  },
  devServer: { ...MiskWebpackConfigBase.devServer,
    port: MiskTabConfig.port
  },
  plugins: []
    .concat(MiskWebpackConfigBase.plugins)
    .concat([CopyWebpackPluginConfig])
}
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
    "output_path": "dist",
    "port": "3200",
    "relative_path_prefix": "_tab/config/",
    "slug": "config"
  }
```

[Releasing](https://github.com/square/misk/blob/master/misk/web/%40misk/RELEASING.md)
---
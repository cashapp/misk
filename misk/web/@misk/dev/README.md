Misk Dev
---
![](https://raw.githubusercontent.com/square/misk/master/misk.png)
This package provides shared devDependencies and compiler options used to provide a common development environment across Misk tab repos. For Typescript linting, see [@misk/tslint](https://www.npmjs.com/package/@misk/tslint).

Getting Started
---
```bash
$ yarn add @misk/dev
```

TsConfig Template
---
Create a `tsconfig.json` file in the repo root directory with the following:

```JSON
  {
    "extends": "./node_modules/@misk/dev/tsconfig.base",
    "compilerOptions": {
        "outDir": "./dist"
    }
  }
```

Webpack Template
---
Create a `webpack.config.js` file in the repo root directory with the following:

```Javascript
const { MiskWebpackConfigBase } = require("@misk/dev")
const path = require('path')
const miskTabWebpack = require(path.join(process.cwd(), "package.json")).miskTabWebpack
module.exports = MiskWebpackConfigBase(process.env.NODE_ENV, {
  "dirname": __dirname,
  miskTabWebpack
},
{
  // optional: any other Webpack config fields to be merged with the Misk Webpack Base Config
})
```

Package.json Input Parameters
---
The child Webpack template above consumes some static initialization variables that you must add to your `package.json`. An example for the Config tab is included below.

Note that the `output_path` must match the `outDir` specified in the repo's `tsconfig.json`.

```JSON
  "devDependencies": {
    "@misk/dev": "^0.0.20"
  },
  "miskTabWebpack": {
    "name": "Config",
    "output_path": "dist",
    "port": "3000",
    "relative_path_prefix": "_tab/config/",
    "slug": "config"
  }
```

Included Libraries
---
From `package.json`:

```
  @types/node
  @types/react
  @types/react-dom
  @types/react-helmet
  @types/react-hot-loader
  @types/react-redux
  @types/react-router
  @types/react-router-dom
  @types/react-router-redux
  @types/webpack
  @types/webpack-env
  awesome-typescript-loader
  copy-webpack-plugin
  cross-env
  css-loader
  file-loader
  html-webpack-plugin
  node-sass
  sass-loader
  source-map-loader
  style-loader
  typescript
  webpack
  webpack-cli
  webpack-dev-server
  webpack-merge
```

[Releasing](https://github.com/square/misk/blob/master/misk/web/%40misk/RELEASING.md)
---
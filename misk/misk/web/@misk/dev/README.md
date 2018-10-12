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
const { miskTabBuilder } = require("@misk/dev")
const path = require('path')
const miskTab = require(path.join(process.cwd(), "package.json")).miskTab
module.exports = miskTabBuilder(process.env.NODE_ENV, {
  "dirname": __dirname,
  miskTab
},
{
  // optional: any other Webpack config fields to be merged with the Misk Tab Webpack Config
})
```

Webpack Externals
---
Used in `miskTabBuilder` but also available as an export of `@misk/dev` are the following externals objects that are formatted for use by Webpack to exclude libraries from compiled code:

- `vendorExternals`: vendor libraries included in `@misk/common/lib/vendors.js`
- `miskExternals`: all Misk libraries

If you are using one of the Webpack builders in `@misk/dev`, all externals above are included in the Webpack config. To use the externals in other Webpack configs, follow the steps below.

- Add the following to your `webpack.config.js` as relevant.
  
  ```Javascript
  const MiskDev = require('@misk/dev')

  ...

  module.exports = {
    mode
    entry
    ...
    externals: { ...MiskDev.vendorExternals, ...MiskDev.miskExternals },
  }

  ```

To build your own externals, use the exported function `makeExternals` which consumes an object of the below structure which maps a key (NPM package name) to a normalized library name that will be mounted on browser `window`.

  ```JSON
  {
    "@blueprintjs/core": ["Blueprint", "Core"],
    "@blueprintjs/icons": ["Blueprint", "Icons"],
    "axios": "Axios",
    ...
  }
  ```

Package.json Input Parameters
---
The child Webpack template above consumes some static initialization variables that you must add to your `package.json`. An example for the Config tab is included below.

Note that the `output_path` must match the `outDir` specified in the repo's `tsconfig.json`.

```JSON
  "devDependencies": {
    "@misk/dev": "^0.0.20"
  },
  "miskTab": {
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